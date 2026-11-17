package com.experian.edf.oxygen.consistencycheck.engines

import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingField
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ConsistencyCheckOps
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ReferenceCacheOps
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import com.experian.edf.oxygen.consistencycheck.standalone.synthesis.SyntheticEventDAO
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.{StateCheck, TaggedConsistencyCheckDictionary}
import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.consistencycheck.domain._
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedConsistencyInfo, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.standalone.dyhydrator.SynthesizedEventsDehydratorService
import com.experian.edf.oxygen.domain.internal.{Frequency, FrequencyUnit, JacksonPeriodUnitSerde, Lineage}
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable


class HighFrequencyEventSynthesizer(oxygenDataAccess: OxygenDataAccess,
                                    syntheticEventStore: String,
                                    syntheticEventDehydrator: SynthesizedEventsDehydratorService,
                                    syntheticEventRealTimeStore: String,
                                    domainDict: DomainDictionaryInternal,
                                    consistencyCheckDictionary: (Map[String, ReferenceCache], Array[TaggedConsistencyCheckDictionary]),
                                    currentDateTime: Long,
                                    lookupService: OxygenLookup,
                                    consistencyLineage: Lineage,
                                    stateCheck: StateCheck) {

  @transient lazy private val logger = getLogger(this.getClass)

  private val dao = new SyntheticEventDAO(
    oxygenDataAccess,
    syntheticEventStore,
    syntheticEventRealTimeStore,
    syntheticEventDehydrator
  )

  /**
   * Perform synthesis of group of events (newEvents) having the same Pin.
   * The new events may have different asOfDate but they must be sorted (ascending) on asOfDate
   *
   * This function is meant to save multiple calls read the saved synthetic events.
   *
   * @param eventsSet
   * @param pin
   * @return
   */
  def performSynthesisEventSet(eventsSet: List[TaggedCuratedEventWithMap],
                               pin: Long,
                               stateCheckEnabled: Boolean): (List[SyntheticEvent], List[TaggedConsistencyInfo]) = {
    // NOTE VAR
    var pastSyntheticEvents = dao.readSyntheticEvents(pin).toList
    var shouldSave = false

    val sortedEvents = eventsSet.sortBy(e => (e.asOfDate, e.lineage.receivedDate))

    // We might need to find a way that can synthesize all events at once instead of calling each event and retrieve
    // for each event on "performSynthesisOnSingleEvent" method
    val allConsistencyResults = mutable.ListBuffer[List[TaggedConsistencyInfo]]()
    sortedEvents.foreach(e => {
      val (updatedSyntheticEvents, consistencyResults) = performSynthesisOnSingleEvent(e,
        pin,
        pastSyntheticEvents,
        stateCheckEnabled)
      if (consistencyResults.orNull != null)
        allConsistencyResults += consistencyResults.get
      // Use the updated Synthetic events as the new pastSyntheticEvents
      pastSyntheticEvents = if (updatedSyntheticEvents.orNull == null)
        pastSyntheticEvents
      else {
        shouldSave = true
        updatedSyntheticEvents.get
      }
    })

    if (shouldSave)
      dao.saveSyntheticEvents(pin, pastSyntheticEvents.toArray)

    (pastSyntheticEvents, allConsistencyResults.flatten.toList)
  }

  def performSynthesisRead(eventToSynthesize: TaggedCuratedEventWithMap,
                           pin: Long,
                           rtKeyValueEntry: KeyValueEntry,
                           stateCheckEnabled: Boolean
                          ): (Option[List[SyntheticEvent]], List[SyntheticEvent], Option[List[TaggedConsistencyInfo]], Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(rtKeyValueEntry, true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version: Long = if (rtValueRaw != null) rtValueRaw._1 else -1L

    val pastSyntheticEventsRaw = if (rtValue != null) {
      val objectMapper = ScalaJacksonUtil.createObjectMapper
      JacksonPeriodUnitSerde.registerWithObjectMapper(objectMapper)
       objectMapper.readValue(rtValue, classOf[Array[SyntheticEvent]])
    //  JsonUtils.fromJson(rtValue, classOf[Array[SyntheticEvent]])
    } else {
      syntheticEventDehydrator.dehydrate(String.valueOf(pin))
    }

    val pastSyntheticEvents = pastSyntheticEventsRaw.toList
    val (updatedSyntheticEvents, consistencyResults) = performSynthesisOnSingleEvent(eventToSynthesize, pin, pastSyntheticEvents, stateCheckEnabled)
    (updatedSyntheticEvents, pastSyntheticEvents, consistencyResults, version)
  }

  def performSynthesisWriteTry(eventToSynthesize: TaggedCuratedEventWithMap,
                               pin: Long,
                               rtKeyValueEntry: KeyValueEntry,
                               stateCheckEnabled: Boolean
                              ): (Boolean, List[SyntheticEvent], List[TaggedConsistencyInfo]) = {
    val (updatedSyntheticEvents, pastSyntheticEvents, consistencyResults, version) = performSynthesisRead(eventToSynthesize, pin, rtKeyValueEntry, stateCheckEnabled)
    if (updatedSyntheticEvents.orNull == null)
      (true, pastSyntheticEvents, consistencyResults.getOrElse(List.empty))
    else {
      val keyValueEntry = new KeyValueEntry()
        .table(syntheticEventRealTimeStore)
        .key(pin.toString)
        .data(ScalaJacksonUtil.toJson(updatedSyntheticEvents.get.toArray).get)
      val isWritten = oxygenDataAccess.setValue(keyValueEntry, true, version)
      (isWritten, updatedSyntheticEvents.get, consistencyResults.getOrElse(List.empty))
    }
  }

  def performSynthesis(eventToSynthesize: TaggedCuratedEventWithMap,
                       pin: Long,
                       stateCheckEnabled: Boolean): (List[SyntheticEvent], List[TaggedConsistencyInfo]) = {

    val rtKeyValueEntry = new KeyValueEntry()
      .table(syntheticEventRealTimeStore)
      .key(pin.toString)

    val retryInterval = 1 // millisecond
    val nTry = 10000
    var iTry = 0
    val syntheticEvents = new mutable.ListBuffer[SyntheticEvent]
    val consistencyResults = new mutable.ListBuffer[TaggedConsistencyInfo]
    while ( {
      val (isWritten, syntheticEventsTmp, consistencyResultsTmp) = performSynthesisWriteTry(eventToSynthesize: TaggedCuratedEventWithMap,
        pin: Long,
        rtKeyValueEntry: KeyValueEntry,
        stateCheckEnabled)
      if (isWritten) {
        syntheticEvents ++= syntheticEventsTmp
        consistencyResults ++= consistencyResultsTmp
      }
      iTry += 1
      if (iTry > nTry) {
        logger.error("Error: HBase concurrent write timeout - performSynthesis")
      }
      (!isWritten) && (iTry <= nTry)
    }) {
      Thread.sleep(retryInterval)
    }
    (syntheticEvents.toList, consistencyResults.toList)
  }


  def createReferenceCacheMapAndPerFormCheck(eventToSynthesize: TaggedCuratedEventWithMap, pastEventToSynthesizeWith: SyntheticEvent, savedEvents: List[SyntheticEvent], pin: Long) = {
    val referenceCacheMap = if (eventToSynthesize.curatedRecordJson.containsKey(SynthesisConstants.kEnableRefCacheLikeNP) &&
      eventToSynthesize.curatedRecordJson.get(SynthesisConstants.kEnableRefCacheLikeNP).toString.toLowerCase().equals(SynthesisConstants.kTrue)) {
      ReferenceCacheOps.createGlobalReferenceList(consistencyCheckDictionary._1, eventToSynthesize, savedEvents, List.empty)
    }
    else {
      ReferenceCacheOps.createGlobalReferenceList(consistencyCheckDictionary._1, eventToSynthesize, List(pastEventToSynthesizeWith), List.empty)
    }
    val (consistencyResults, fieldsToFilter, shouldFilterEvent) =
      ConsistencyCheckOps.performAllChecks(consistencyCheckDictionary._2,
        pin,
        eventToSynthesize,
        List.empty[SyntheticEvent],
        savedEvents,
        currentDateTime,
        lookupService,
        consistencyLineage,
        mutable.HashMap.empty[Int, RelationshipMigrationTrackingField],
        Some(referenceCacheMap),
        stateCheck)
    (consistencyResults, fieldsToFilter, shouldFilterEvent)
  }

  /**
   * Perform systhesis of a new event and synthesized events for a pin
   *
   * @param eventToSynthesize
   * @param pin
   * @return
   */
  def performSynthesisOnSingleEvent(eventToSynthesize: TaggedCuratedEventWithMap,
                                    pin: Long,
                                    pastSyntheticEvents: List[SyntheticEvent],
                                    stateCheckEnabled: Boolean): (Option[List[SyntheticEvent]], Option[List[TaggedConsistencyInfo]]) = {

    /**
     * Retrieve the saved synthesized events
     */
    val savedEvents = if (pastSyntheticEvents == null)
      dao.readSyntheticEvents(pin).toList
    else
      pastSyntheticEvents

    logger.debug(s"past synthetic events size ${savedEvents.size}")

    if (stateCheckEnabled) {
      val stateName = stateCheck.getStateCheckFields.keys
      if (stateName.nonEmpty) {
        val sortedList = savedEvents
          .filter(x => x.frequency.unit == FrequencyUnit("Unique"))
          .sortBy(x => x.asOfPeriod)
        stateName.foreach(eachName => {
          sortedList.foreach(eachEvent =>
            if (eachEvent.curatedRecordJson.containsKey(eachName._1)) {
              logger.debug(s"will check if we need to update stateCheck with following synthetic event of \n${eachEvent}")
              stateCheck.update(eachEvent)
            })
        })
      }
    }

    /**
     * Check to see if new event is unique frequency
     * If not unique, it will synthesize with previous
     * events.
     *
     * Otherwise, it will just add a new event to previous events.
     */
    if (checkUnique(eventToSynthesize)) {
      // Group for same period and different period with the new event
      val (samePeriod, differentPeriod) = if (savedEvents.isEmpty) (null, null)
      else {
        val samePeriod = savedEvents.filter(x => {
          getSamePeriodEvent(eventToSynthesize, x)
        })
        val differentPeriod = savedEvents.filter(x => {
          !getSamePeriodEvent(eventToSynthesize, x)
        })
        if (samePeriod.isEmpty)
          (null, differentPeriod)
        else
          (samePeriod, differentPeriod)
      }

      val pastEventToSynthesizeWith =
        if (samePeriod == null && (samePeriod == null && differentPeriod == null)) null
        else if (samePeriod != null) {
          samePeriod
            .sortBy(x =>
              x.asOfPeriod)
            .last
        }
        else {
          val currentEventPeriod = eventToSynthesize.getPeriod

          // Getting the latest period from different period. This is mainly for getting correct data for LAP
          // Current Data
          // Saved Data
          // Jan 18 LAP, { mailing Address: "123 Good place in San jose, CA"}
          // Feb 18 LAP, { mailing Address: "123 Good place in San jose, CA"}
          // Arp 18 LAP, { mailing Address: "123 Good place in San jose, CA"}

          // When new data comes in for period Mar 18, LAP missing field for mailing address,
          // it will get data from Feb 18.
          val latestFromDifferentPeriod = differentPeriod
            .filter(x => x.asOfPeriod < currentEventPeriod)
            .filter(x => x.asOfPeriod.unit == eventToSynthesize.getPeriod.unit)
            .sortBy(x => x.asOfPeriod)
          val returnIfNotEmpty = if (latestFromDifferentPeriod.isEmpty) null else latestFromDifferentPeriod.last
          returnIfNotEmpty
        }
      // If the event asOfData is less then last synthetic event
      // then we do not perform HF synthesize for this.  In this case we return an empty array.
      val (newSynthesizedEvents, consistencyResults) = if (canHighFrequencySynthesize(eventToSynthesize, pastEventToSynthesizeWith)) {

        val (consistencyResults, fieldsToFilter, shouldFilterEvent) = createReferenceCacheMapAndPerFormCheck(eventToSynthesize, pastEventToSynthesizeWith, savedEvents, pin)
        // exclude the events or fields and generate partial synthetic event
        val newSynthesizedEvents = if (!shouldFilterEvent) {
          if (fieldsToFilter.nonEmpty) {
            fieldsToFilter.foreach(field => {
              logger.debug("---| Excluding field " + fieldsToFilter + " for event with event Id " + eventToSynthesize.eventId + " because of inconsistency")
              TaggedCuratedEventWithMap.excludeField(field, eventToSynthesize)
            })
          }
          // Create a new EUC
          val euc = new EUC(domainDict)
          // Add EUC fields from the already synthesized events
          euc.update(pastEventToSynthesizeWith)
          // Update EUC fields from the newly received events
          euc.update(eventToSynthesize)
          // Create a new LAP
          val lap = new LAP(domainDict)
          // Add LAP fields from the already synthesized events
          lap.update(pastEventToSynthesizeWith)
          // Update LAP fields from the newly received events
          lap.update(eventToSynthesize)

          // If the new event asOfPeriod is greater than the last synthetic event then
          // we need to create a new synthetic event and append to the synthesized events.
          val updatedSyntheticEvents = if (pastEventToSynthesizeWith == null || eventToSynthesize.getPeriod > pastEventToSynthesizeWith.asOfPeriod) {
            val newSyntheticEvent = IncrementalEventSynthesizer.synthesize(eventToSynthesize,
              null, domainDict.fields, euc, lap)

            newSyntheticEvent +: savedEvents
          } else {
            val partialSyntheticEventGroupedBySR = mutable.HashMap[Int, SyntheticEvent]()
            samePeriod.foreach(eachSynthetic => {
              val k = getSRKey(eventToSynthesize.subscriber, eventToSynthesize.subscriberRelation)
              val partialSyntheticEvent = IncrementalEventSynthesizer.synthesize(eventToSynthesize, eachSynthetic, domainDict.fields, euc, lap)
              partialSyntheticEventGroupedBySR.put(k, partialSyntheticEvent)
            })
            partialSyntheticEventGroupedBySR.take(1).head._2 +: differentPeriod
          }
          euc.propagate(updatedSyntheticEvents)
          Some(updatedSyntheticEvents)
        }
        else {
          logger.debug("---| Excluding event with event Id " + eventToSynthesize.eventId + " because of inconsistency")
          None
        }
        (newSynthesizedEvents, Some(consistencyResults))
      } else {
        // TODO: can this event be tracked...need to send it to error?
        logger.error(s"Cannot synthesize for following events: \n$pastEventToSynthesizeWith \n$pastEventToSynthesizeWith")
        (None, None)
      }
      (newSynthesizedEvents, consistencyResults)
    } else {
      val (consistencyResults, fieldsToFilter, shouldFilterEvent) = createReferenceCacheMapAndPerFormCheck(eventToSynthesize, null, savedEvents, pin)
      val newSynthesizedEvents = if (!shouldFilterEvent) {
        if (fieldsToFilter.nonEmpty) {
          fieldsToFilter.foreach(field => {
            logger.debug("---| Excluding field " + fieldsToFilter + " for event with event Id " + eventToSynthesize.eventId + " because of inconsistency")
            TaggedCuratedEventWithMap.excludeField(field, eventToSynthesize)
          })
        }
        val newSyntheticEvent = createSyntheticEvtFromCuratedEvt(eventToSynthesize)

        val updatedSyntheticEvents = newSyntheticEvent +: savedEvents
        Some(updatedSyntheticEvents)
      }
      else {
        logger.debug("---| Excluding event with event Id " + eventToSynthesize.eventId + " because of inconsistency")
        None
      }
      (newSynthesizedEvents, Some(consistencyResults))
    }
  }

  /**
   * An event with frequency Unique just adds to synthesize result
   *
   * @param event
   * @return
   */
  private def checkUnique(event: TaggedCuratedEventWithMap): Boolean = {
    if (event.minFrequency == Frequency.unique)
      false
    else
      true
  }

  /**
   * If the event asOfdate is less then last synthetic event then
   * this event cannot be synthesized in HF mode.
   *
   * @param event
   * @param otherEvent
   * @return
   */
  private def canHighFrequencySynthesize(event: TaggedCuratedEventWithMap,
                                         otherEvent: SyntheticEvent): Boolean = {
    if (event.minFrequency == Frequency.unique ||
      otherEvent == null ||
      (event.subscriber != otherEvent.subscriber &&
        event.subscriberRelation != otherEvent.subscriberRelation)) true
    else {
      if (event.getPeriod.epochDateTime >= otherEvent.asOfPeriod.epochDateTime) true
      else {
        logger.debug(s"Fail: Cannot synthesized events. This event with synthesizedEvents condition: ${(event.getPeriod.epochDateTime >= otherEvent.asOfPeriod.epochDateTime)} ${event.getPeriod.epochDateTime} >= ${otherEvent.asOfPeriod.epochDateTime}")
        false
      }
    }
  }

  private def getSamePeriodEvent(candidateEvent: TaggedCuratedEventWithMap,
                                 eventToSynthesizeWith: SyntheticEvent): Boolean = {
    candidateEvent.subscriber == eventToSynthesizeWith.subscriber &&
      candidateEvent.subscriberRelation == eventToSynthesizeWith.subscriberRelation &&
      //      candidateEvent.asOfPeriod.id == eventToSynthesizeWith.asOfPeriod.id &&
      candidateEvent.asOfPeriod == eventToSynthesizeWith.asOfPeriod &&
      !eventToSynthesizeWith.frequency.isEqual(Frequency.unique)
  }

  private def getDifferentPeriodEvent(candidateEvent: TaggedCuratedEventWithMap,
                                      eventToSynthesizeWith: SyntheticEvent): Boolean = {
    candidateEvent.subscriber == eventToSynthesizeWith.subscriber &&
      candidateEvent.subscriberRelation == eventToSynthesizeWith.subscriberRelation &&
      //      candidateEvent.asOfPeriod.id != eventToSynthesizeWith.asOfPeriod.id &&
      candidateEvent.asOfPeriod != eventToSynthesizeWith.asOfPeriod &&
      !eventToSynthesizeWith.frequency.isEqual(Frequency.unique)
  }

  private def createSyntheticEvtFromCuratedEvt(eventToSynthesize: TaggedCuratedEventWithMap): SyntheticEvent = {
    val syntheticEvent = SyntheticEvent(eventToSynthesize.lineage.dataSource,
      eventToSynthesize.subscriber,
      eventToSynthesize.subscriberRelation,
      eventToSynthesize.minFrequency,
      eventToSynthesize.asOfPeriod,
      Array(eventToSynthesize.eventId),
      eventToSynthesize.curatedRecordJson,
      Array.empty)
    syntheticEvent
  }

  private[consistencycheck] def getSRKey(sub: String, rel: String): Int = {
    (sub + ":" + rel).hashCode
  }
}
