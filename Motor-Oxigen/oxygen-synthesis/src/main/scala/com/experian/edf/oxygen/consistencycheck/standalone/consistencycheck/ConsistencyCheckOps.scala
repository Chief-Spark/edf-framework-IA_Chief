package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import java.time.{Instant, ZoneId}
import com.experian.edf.oxygen.domain.{ConsistencyInfo, SyntheticEvent, TaggedConsistencyInfo, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.domain.processors.ConsistencyCheckProcessorContext
import com.experian.edf.oxygen.consistencycheck.domain._
import com.experian.edf.oxygen.logging.performance.PerformanceLogger.timeIt
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import com.experian.edf.oxygen.consistencycheck.standalone.migration.{RelationshipMigrationChecker, RelationshipMigrationProcessor, RelationshipMigrationTrackingField, RelationshipMigrationTrackingLogging}
import com.experian.edf.oxygen.consistencycheck.standalone.parser.ast._
import com.experian.edf.oxygen.consistencycheck.standalone.parser.base.{ConsistencyInfoParser, ShouldFilterEventParser}
import com.experian.edf.oxygen.consistencycheck.standalone.utils.HCUtil
import com.experian.edf.oxygen.domain.internal.Lineage
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.slf4j.LoggerFactory.getLogger

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.io.BufferedSource
import scala.util.control.Breaks.{break, breakable}

/**
 * All the consistency check helper and util methods are present here
 *
 */
object ConsistencyCheckOps extends Serializable {

  @transient lazy val logger = getLogger(this.getClass)


  /**
   * 1. Perform non periodic checks
   * 2. Group events by period
   * 3. --for each period in the hc
   * 4. ----for each event with earliest received date
   * 5. ------Perform Check - if check return false, raise consistency error, and filter event based on configuration
   * 6. --------Synthesize with the previous data for each subscriber-subscriber relation
   * 7. --------Perform relationship migration
   *
   * Other features handled while performing consistency checks and synthesis are state check and infer delete
   *
   * Inference of deletes: There are cases where a new incoming file contains events representing a new state that
   * overrides the previous state.  In some such cases, an extension may want the framework to ignore previously
   * ingested events. -Oxygen+Release++-+0.18.11.19
   *
   * state check - usecase: disputes Oxygen+Release+-0.98.191127.61
   *
   * @param checks
   * @param hc
   * @param domainDict
   * @return
   */
  private[oxygen] def performChecks(checks: Array[TaggedConsistencyCheckDictionary],
                                    hc: HeterogeneousCollection,
                                    domainDict: DomainDictionaryInternal,
                                    currDateTime: Long,
                                    lookupService: OxygenLookup,
                                    relationshipMigrationProcessor: RelationshipMigrationProcessor,
                                    referenceCacheConfig: Map[String, ReferenceCache],
                                    synthesisOptions: SynthesisOptions,
                                    fieldHandlerHashFunction: String): (List[TaggedConsistencyInfo], List[SyntheticEvent], List[RelationshipMigrationTrackingLogging]) = {

    val consistencyLineage = ConsistencyCheckOps.getLineageForConsistencyInfo(currDateTime)


    lazy val numberOfAccountsInHC = Util.getNumberOfAccountsInHC(hc)
    logger.debug(s"Number of Accounts in HC : $numberOfAccountsInHC")

    val filteredChecks = if (synthesisOptions.consistencyCheckCategory.nonEmpty && checks.nonEmpty) {
      ConsistencyCheckOps.getFilteredChecks(checks, synthesisOptions.consistencyCheckCategory, numberOfAccountsInHC)
    } else checks

    synthesisOptions.consistencyCheckCategory.foreach(category => logger.debug("Category : " + category.toString))
    logger.debug(s"Total Checks : ${checks.length} - Checks Performed : ${filteredChecks.length} - Checks that got filtered: ${checks.length - filteredChecks.length}")

    // Initialize empty list for synthetic event, EUC, and LAP, stateCheck
    val syntheticEvents = ListBuffer[SyntheticEvent]()
    val (stateName, stateId, stateCheckEnabled) = (synthesisOptions.stateCheckAttributes.stateName, synthesisOptions.stateCheckAttributes.stateId, synthesisOptions.stateCheckAttributes.stateCheckEnabled)

    val stateAttributes = ConsistencyCheckOps.readStateAttribute(stateName, stateId)
    val euc = new EUC(domainDict,fieldHandlerHashFunction)
    val lap = new LAP(domainDict,fieldHandlerHashFunction)
    val stateCheck = new StateCheck(stateAttributes)
    val allResults = ListBuffer[TaggedConsistencyInfo]()

    val nonPeriodicCheckEvents: (Array[TaggedCuratedEventWithMap], List[TaggedConsistencyInfo]) = ConsistencyCheckOps.performNonPeriodicChecks(
      hc,
      filteredChecks,
      consistencyLineage,
      currDateTime,
      lookupService,
      mutable.HashMap.empty[Int, RelationshipMigrationTrackingField],
      referenceCacheConfig,
      synthesisOptions.referenceEventWindowInMonths
    )
    lazy val (migrationTrackerWithSource, migrationTrackerWithDest,migrationTrackerMap) = ConsistencyCheckOps.createRelationshipMigrationTracker(hc.taggedCuratedEvents, relationshipMigrationProcessor, synthesisOptions.migrationSubscriberLoggingEnabled)

    val (allEventsGroupedByPeriod, filledInStateCheck) = HCUtil.groupEventsByPeriod(nonPeriodicCheckEvents._1, stateCheck, stateCheckEnabled, synthesisOptions.fileTypeTimeMap, synthesisOptions.checkFileTagEnabled, synthesisOptions.dateTypePick)

    // Add non-periodic consistency info to allResults
    allResults.++=:(nonPeriodicCheckEvents._2)


    logger.debug(s"-| Number of Periods: {}", allEventsGroupedByPeriod.length.toString)
    val referenceCacheData = new CheckReferenceCacheManager(referenceCacheConfig)
//    logger.info("Running with CheckReferenceCacheManager")

    allEventsGroupedByPeriod.foreach(x => {
      val currentPeriod = x._1
      val (eventsByPeriod, deleteInferredInfo) = if (synthesisOptions.contextualOverrideAttributes.shouldFilterDeletedEvents) {
        val filteredEventWithLogInfo = ConsistencyCheckOps.filterDeletedEvents(x._2.toArray, hc.pin, synthesisOptions.contextualOverrideAttributes.shouldReportDeletedEvents, currDateTime)
        (filteredEventWithLogInfo._1.sortBy(e => (e.asOfDate, e.lineage.receivedDate)), filteredEventWithLogInfo._2)
      }
      else
        (x._2.toArray.sortBy(e => (e.asOfDate, e.lineage.receivedDate)), List[TaggedConsistencyInfo]())

      allResults.++=:(deleteInferredInfo)

      logger.debug(s"-| Performing consistency check for period: {} , Number of Events : {}", currentPeriod.id.toString, eventsByPeriod.length)
      val periodicChecks = filteredChecks.filter(!_.isNonPeriodic)

      val partialSyntheticEventGroupedBySR = mutable.HashMap[Int, SyntheticEvent]()
      referenceCacheData.clearPartiallySyntheisedCache()

      eventsByPeriod.foreach(subjectEvent => {

        logger.trace(escapeJava(s"---| Processing event with event Id/DS/Sub/Rel: {}/{}/{}/{} "), escapeJava(subjectEvent.eventId.toString),
          escapeJava(subjectEvent.lineage.dataSource), escapeJava(subjectEvent.subscriber), escapeJava(subjectEvent.subscriberRelation))


        val partiallySynthesizedEvents = partialSyntheticEventGroupedBySR.values.toList
        // perform only checks that are not "nonPeriodic"

        //createReferenceList
        //val referenceCacheMap = ReferenceCacheOps.createGlobalReferenceList(referenceCacheConfig, subjectEvent, syntheticEvents.toList, partiallySynthesizedEvents)
        val timedReferenceCacheMap = timeIt(referenceCacheData.retrieveFromCache(subjectEvent))
        if (timedReferenceCacheMap.duration.toMillis > 1) logger.debug(s"ConsistencyCheckOps.createGlobalReferenceList - ${timedReferenceCacheMap.asMillis}")
        val referenceCacheMap = timedReferenceCacheMap.result

        // TODO: tobe determined if it has to be removed z
        val limitedSyntheticEvents = if (synthesisOptions.referenceEventWindowInMonths.isDefined) {
          val subjectEventDate = Instant.ofEpochMilli(subjectEvent.asOfDate).atZone(ZoneId.of("Etc/UTC"))
          val referenceEventTimeWindowStart: Long = subjectEventDate.minusMonths(synthesisOptions.referenceEventWindowInMonths.get).toEpochSecond * 1000
          syntheticEvents.filter(_.asOfPeriod.epochDateTime >= referenceEventTimeWindowStart).toList
        }
        else syntheticEvents.toList
        val (consistencyResults, fieldsToFilter, shouldFilterSubjectEvent) =
          ConsistencyCheckOps.performAllChecks(periodicChecks,
            hc.pin,
            subjectEvent,
            partiallySynthesizedEvents,
            //syntheticEvents.toList,
            limitedSyntheticEvents,
            currDateTime,
            lookupService,
            consistencyLineage,
            migrationTrackerWithDest,
            Some(referenceCacheMap),
            filledInStateCheck)

        if (consistencyResults.nonEmpty) allResults.++=:(consistencyResults)

        // exclude the events or fields and generate partial synthetic event
        if (!shouldFilterSubjectEvent) {
          if (fieldsToFilter.nonEmpty) {
            fieldsToFilter.foreach(field => {
              logger.trace("---| Excluding field " + fieldsToFilter +
                " for event with event Id " + subjectEvent.eventId + " because of inconsistency", subjectEvent.eventId)
              TaggedCuratedEventWithMap.excludeField(field, subjectEvent)
            })
          }
          euc.update(subjectEvent)
          lap.update(subjectEvent)
          val k = ConsistencyCheckOps.getSRKey(subjectEvent.subscriber, subjectEvent.subscriberRelation)
          val p = partialSyntheticEventGroupedBySR.get(k).orNull
          val partialSyntheticEvent = IncrementalEventSynthesizer.synthesize(subjectEvent, p, domainDict.fields, euc, lap)

          partialSyntheticEventGroupedBySR.put(k, partialSyntheticEvent)
          referenceCacheData.addToPartiallySynthesisedCache(partialSyntheticEvent)
        }
        else {
          logger.trace(s"---| Excluding event with event Id {} because of inconsistency", subjectEvent.eventId)
          // If statecheck is enabled the filtered event has to be removed and the state table has to be refreshed
          if (stateCheckEnabled) {
            logger.trace(s"---| verifying State Check for event Id {} because of inconsistency", subjectEvent.eventId)
            stateCheck.updateAfterFilteringEvent(subjectEvent)
          }
        }
      })


      partialSyntheticEventGroupedBySR.foreach(x => {
        x._2 +=: syntheticEvents
        referenceCacheData.addToSynthesisedCache(x._2)
      })

      val syntheticEventsForThisPeriod = partialSyntheticEventGroupedBySR.values.toList

      // performing account renumbering on all the synthetic events
      if (relationshipMigrationProcessor == null) {
        logger.trace(s"--| No migration processor found. Skipping migration.")
        //syntheticEvents.toList
      }
      else {
        logger.trace(s"--| Performing account migration")
        val (refreshedEvents, migratedEvents, removedEvents) = RelationshipMigrationChecker.performRelationshipMigration2(syntheticEvents.toList, syntheticEventsForThisPeriod, relationshipMigrationProcessor, euc, lap, migrationTrackerWithSource)
        // clear synthetic event list buffer and load it with new list of migrated events
        syntheticEvents.clear()
        logger.trace(s"---|adding migrated events to synthetic events")
        syntheticEvents.++=:(refreshedEvents)
        referenceCacheData.removeFromCache(removedEvents)
        migratedEvents.foreach(referenceCacheData.addToSynthesisedCache)
      }
    })
    val syntheticEventsList = syntheticEvents.toList
    // propagate EUC to all events
    euc.propagate(syntheticEventsList)
    (allResults.toList, syntheticEvents.toList,migrationTrackerMap.values.toList)
  }

  private[oxygen] def performAllChecks(checks: Array[TaggedConsistencyCheckDictionary],
                                       pin: Long,
                                       subjectEvent: TaggedCuratedEventWithMap,
                                       partiallySynthesizedEvents: List[SyntheticEvent],
                                       syntheticEvents: List[SyntheticEvent],
                                       currDateTime: Long,
                                       lookup: OxygenLookup,
                                       consistencyLineage: Lineage,
                                       relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                       referenceCacheMap: Option[java.util.Map[String, List[SyntheticEvent]]] = None,
                                       stateCheck: StateCheck): (List[TaggedConsistencyInfo], List[String], Boolean) = {

    var shouldFilterSubjectEvent = false
    val fieldsToFilter = new ListBuffer[String]
    val consistencyResults = new ListBuffer[TaggedConsistencyInfo]()
    //val sortedThatEvents = syntheticEvents.sortBy(_.asOfPeriod)(Ordering[Period])

    breakable {
      checks.foreach(x => {
        val checkName = x.checkName
        val checkDefinition = x

        logger.trace(s"----| Performing check: {} ", checkName)
        val startTImeForCheck = System.currentTimeMillis()
        val (consistencyInfo, filteredFields, shouldFilterEventOnError) = performCheck(checkName,
          checkDefinition,
          pin,
          subjectEvent,
          partiallySynthesizedEvents,
          syntheticEvents,
          currDateTime,
          lookup,
          consistencyLineage,
          relationshipMigrationTracker,
          referenceCacheMap,
          stateCheck)
        val endTimeForCheck = System.currentTimeMillis()
        val totalTime = endTimeForCheck - startTImeForCheck

        //   logger.error(checkName + ": " + totalTime.toString )

        shouldFilterSubjectEvent = shouldFilterSubjectEvent || shouldFilterEventOnError
        if (consistencyInfo != null) consistencyResults.++=:(consistencyInfo)
        if (filteredFields != null && filteredFields.nonEmpty) fieldsToFilter.++=:(filteredFields)

        if (shouldFilterSubjectEvent)
          break()
      })
    }

    (consistencyResults.toList, fieldsToFilter.toList, shouldFilterSubjectEvent)
  }

  private[oxygen] def performCheck(checkName: String,
                                   checkDefinition: TaggedConsistencyCheckDictionary,
                                   pin: Long,
                                   subjectEvent: TaggedCuratedEventWithMap,
                                   partiallySynthesizedEvents: List[SyntheticEvent],
                                   syntheticEvents: List[SyntheticEvent],
                                   currDateTime: Long,
                                   lookup: OxygenLookup,
                                   consistencyLineage: Lineage,
                                   relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                   referenceCacheMap: Option[java.util.Map[String, List[SyntheticEvent]]] = None,
                                   stateCheck: StateCheck): (List[TaggedConsistencyInfo], List[String], Boolean) = {
    try {
      logger.trace(s"----| Performing check: {}", checkName)

      if (evaluateTriggerCondition(checkDefinition, subjectEvent)) {
        logger.trace(s"-----| TriggerCondition, {}, is true", checkDefinition.triggerCondition)
        val withEvents = getReferenceEvents(checkName,
          checkDefinition,
          syntheticEvents,
          partiallySynthesizedEvents,
          subjectEvent,
          currDateTime,
          referenceCacheMap)
        // if processor is available
        if (checkDefinition.checkProcessor != null.asInstanceOf[ConsistencyCheckProcessor]) {

          logger.trace(s"-----| Found custom implementation, will use that for check: {} ", checkName)
          val check = checkDefinition.checkProcessor
          val res = check.performChecks(subjectEvent, withEvents)
          if (res._1 == null || res._1.isEmpty) {
            logger.trace("-----| Did not find inconsistency for check, " + checkName +
              ", for eventId:" + subjectEvent.eventId)
            (List.empty, List.empty, false)
          } else {

            val (filteredFields, shouldFilterEvent) = if (res._2) (checkDefinition.filterFieldsOnError, res._2) else shouldFilter(checkName, checkDefinition, subjectEvent, lookup)

            val consistencyInfoList = res._1.map(res => TaggedConsistencyInfo(pin,
              consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage,
              checkName, subjectEvent.eventId, res, shouldFilterEvent))
            (consistencyInfoList, filteredFields, shouldFilterEvent)
          }
        }
        // if single check is defined
        else if (checkDefinition.check.isDefined) {
          val checkASTNode = checkDefinition.check
          val astOut = CheckGenerator.traverseAST(checkASTNode.get, subjectEvent, withEvents, CheckASTObject(lookup, relationshipMigrationTracker), stateCheck)
          if (!astOut) {
            val (filteredFields, shouldFilterEvent) = shouldFilter(checkName, checkDefinition, subjectEvent, lookup)

            val consistencyInfo = TaggedConsistencyInfo(pin,
              consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage,
              checkName, subjectEvent.eventId, checkDefinition.consistencyInfo, shouldFilterEvent)
            (List(consistencyInfo), filteredFields, shouldFilterEvent)
          } else {
            logger.trace("-----| Did not find inconsistency for check, " + checkName + ", for eventId: " + subjectEvent.eventId)
            (List.empty, List.empty, false)
          }
        } // if multiple checks are defined
        else {
          logger.trace(s"-----| Found multiple checks grouped for check: {} ", checkName)
          val checksList = checkDefinition.checksWithInfo
          val filteredFieldsList = ListBuffer[String]()
          val consistencyInfoList = ListBuffer[TaggedConsistencyInfo]()
          val shouldFilterBuffer = ListBuffer[Boolean]()
          breakable {
            checksList.foreach(eachCheck => {
              val checkResult = evaluateCheckForPeriodic(pin, checkName, eachCheck, subjectEvent, withEvents, lookup,
                relationshipMigrationTracker, consistencyLineage, stateCheck)
              filteredFieldsList.++=:(checkResult._2)
              consistencyInfoList.++=:(checkResult._1)
              shouldFilterBuffer.+=:(checkResult._3)
              if (checkResult._3) break()
            })
          }
          (consistencyInfoList.toList, filteredFieldsList.toList, shouldFilterBuffer.head)
        }
      } else {
        (List.empty, List.empty, false)
      }
    }
    catch {
      case e: Exception => {
        logger.trace(s"Error for the pin : ${e.getMessage} for pin ${pin} for check ${checkName} for eventId ${subjectEvent.eventId}")
        logger.trace(s"stack trace for pin ${pin} for check ${checkName} for eventId ${subjectEvent.eventId}", e)
        (List.empty, List.empty, false)
      }
    }
  }

  /**
   *
   * @param hc
   * @param checks
   * @param consistencyLineage
   * @return
   */
  private[oxygen] def performNonPeriodicChecks(hc: HeterogeneousCollection,
                                               checks: Array[TaggedConsistencyCheckDictionary],
                                               consistencyLineage: Lineage,
                                               currentDateTime: Long,
                                               lookupService: OxygenLookup,
                                               relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                               referenceCache: Map[String, ReferenceCache],
                                               referenceEventWindowInMonths: Option[Int]): (Array[TaggedCuratedEventWithMap], List[TaggedConsistencyInfo]) = {
    val nonPeriodicCheckList = new ListBuffer[TaggedCuratedEventWithMap]

    // Filter Non-periodic checks
    val nonPeriodicChecks = checks.filter(_.isNonPeriodic)
    val allResults = new ListBuffer[TaggedConsistencyInfo]
    val refCacheMap = ConsistencyCheckOps.createGlobalReferenceListNonPeriodic(referenceCache, hc.taggedCuratedEvents.toList)
    if (nonPeriodicChecks.nonEmpty) {

      logger.trace(s"--| Performing non-periodic consistency check")
      hc.taggedCuratedEvents.foreach(subjectEvent => {
        val (consistencyResults, fieldsToFilter, shouldFilterSubjectEvent) =
          ConsistencyCheckOps.performAllNonPeriodicChecks(nonPeriodicChecks,
            hc.pin,
            subjectEvent,
            hc.taggedCuratedEvents,
            currentDateTime,
            lookupService,
            consistencyLineage,
            relationshipMigrationTracker,
            refCacheMap,
            referenceEventWindowInMonths)

        if (consistencyResults.nonEmpty) allResults.++=:(consistencyResults)

        // exclude the events or fields if any
        if (!shouldFilterSubjectEvent) {
          if (fieldsToFilter.nonEmpty) {
            fieldsToFilter.foreach(field => {
              logger.trace("---| Excluding field " + fieldsToFilter + " for event with event Id " + subjectEvent.eventId + " because of inconsistency")
              TaggedCuratedEventWithMap.excludeField(field, subjectEvent)
            })
          }
          nonPeriodicCheckList.+=:(subjectEvent)
        }
        else {
          logger.trace(s"---| Excluding event with event Id {} because of inconsistency", subjectEvent.eventId)
        }
      })
      (nonPeriodicCheckList.toArray, allResults.toList)
    }
    else
      (hc.taggedCuratedEvents, allResults.toList)
  }


  private[oxygen] def performAllNonPeriodicChecks(checks: Array[TaggedConsistencyCheckDictionary],
                                                  pin: Long,
                                                  subjectEvent: TaggedCuratedEventWithMap,
                                                  taggedCuratedEvents: Array[TaggedCuratedEventWithMap],
                                                  currDateTime: Long,
                                                  lookup: OxygenLookup,
                                                  consistencyLineage: Lineage,
                                                  relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                                  referenceCacheMap: java.util.HashMap[String, (List[String], java.util.HashMap[String, ListBuffer[TaggedCuratedEventWithMap]])],
                                                  referenceEventWindowInMonths: Option[Int]): (List[TaggedConsistencyInfo], List[String], Boolean) = {

    var shouldFilterSubjectEvent = false
    val fieldsToFilter = new ListBuffer[String]
    val consistencyResults = new ListBuffer[TaggedConsistencyInfo]()
    //val sortedThatEvents = taggedCuratedEvents.sortBy(_.asOfPeriod)(Ordering[Period])

    breakable {
      checks.foreach(x => {
        val checkName = x.checkName
        val checkDefinition = x

        logger.trace(s"----| Performing check: {}", checkName)

        val (consistencyInfo, filteredFields, shouldFilterEventOnError) = performNonPeriodicCheck(checkName,
          checkDefinition,
          pin,
          subjectEvent,
          taggedCuratedEvents,
          currDateTime,
          lookup,
          consistencyLineage,
          relationshipMigrationTracker,
          referenceCacheMap,
          referenceEventWindowInMonths)

        shouldFilterSubjectEvent = shouldFilterSubjectEvent || shouldFilterEventOnError
        if (consistencyInfo != null) consistencyResults.++=:(consistencyInfo)
        if (filteredFields != null && filteredFields.nonEmpty) fieldsToFilter.++=:(filteredFields)

        if (shouldFilterSubjectEvent)
          break()

      })

    }

    (consistencyResults.toList, fieldsToFilter.toList, shouldFilterSubjectEvent)
  }


  private[oxygen] def performNonPeriodicCheck(checkName: String,
                                              checkDefinition: TaggedConsistencyCheckDictionary,
                                              pin: Long,
                                              subjectEvent: TaggedCuratedEventWithMap,
                                              taggedCuratedEvents: Array[TaggedCuratedEventWithMap],
                                              currentDateTime: Long,
                                              lookupService: OxygenLookup,
                                              consistencyLineage: Lineage,
                                              relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                              referenceCacheMap: java.util.HashMap[String, (List[String], java.util.HashMap[String, ListBuffer[TaggedCuratedEventWithMap]])],
                                              referenceEventWindowInMonths: Option[Int]): (List[TaggedConsistencyInfo], List[String], Boolean) = {
    try {
      logger.trace(s"---| Processing event with event Id/DS/Sub/Rel: {}/{}/{}/{} ", subjectEvent.eventId.toString,
        subjectEvent.lineage.dataSource,
        subjectEvent.subscriber,
        subjectEvent.subscriberRelation)

      logger.trace(s"----| Performing check: {} ", checkName)

      if (evaluateTriggerCondition(checkDefinition, subjectEvent)) {
        val withEvents = getReferenceEventsForNonPeriodic(checkName,
          checkDefinition,
          taggedCuratedEvents,
          subjectEvent,
          currentDateTime,
          referenceCacheMap,
          referenceEventWindowInMonths)
        // if custom class is defined
        if (checkDefinition.checkProcessor != null.asInstanceOf[ConsistencyCheckProcessor]) {

          logger.trace(s"-----| Found custom implementation, will use that for check: {}", checkName)
          val check = checkDefinition.checkProcessor
          //val check = Class.forName(checkDefinition.className.trim).newInstance.asInstanceOf[ConsistencyCheckProcessor]
          val res = check.performNonPeriodicChecks(subjectEvent, withEvents)

          if (res._1 == null || res._1.isEmpty) {
            logger.trace("-----| Did not find inconsistency for check, " + checkDefinition.triggerCondition +
              ", for eventId: " + subjectEvent.eventId)
            (List.empty, List.empty, false)
          } else {
            val (filteredFields, shouldFilterEvent) = if (res._2) (checkDefinition.filterFieldsOnError, res._2) else shouldFilter(checkName, checkDefinition, subjectEvent, lookupService)

            val taggedResList = res._1.map(eachCInfo => TaggedConsistencyInfo(pin,
              consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage,
              checkName, subjectEvent.eventId, eachCInfo, shouldFilterEvent))
            (taggedResList, filteredFields, shouldFilterEvent)
          }
        }
        // if single check is defined
        else if (checkDefinition.check.isDefined) {
          val checkASTNode = checkDefinition.check
          val astOut = CheckGenerator.traverseASTForNonPeriodic(checkASTNode.get, subjectEvent, withEvents, CheckASTObject(lookupService, relationshipMigrationTracker))
          if (!astOut) {
            val (filteredFields, shouldFilterEvent) = shouldFilter(checkName, checkDefinition, subjectEvent, lookupService)

            val taggedRes = TaggedConsistencyInfo(pin,
              consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage, checkName, subjectEvent.eventId, checkDefinition.consistencyInfo, shouldFilterEvent)

            (List(taggedRes), filteredFields, shouldFilterEvent)
          } else {
            // logger.trace(s"-----| Did not find inconsistency for check, {}, for eventId: {}",checkName , subjectEvent.eventId)
            (List.empty, List.empty, false)
          }
        }
        // if multiple checks are defined
        else {
          val checksList = checkDefinition.checksWithInfo
          val filteredFieldsList = ListBuffer[String]()
          val consistencyInfoList = ListBuffer[TaggedConsistencyInfo]()
          val shouldFilterBuffer = ListBuffer[Boolean]()
          breakable {
            checksList.foreach(eachCheck => {
              val checkResult = evaluateCheckForNonPeriodic(pin, checkName, eachCheck, subjectEvent, withEvents, lookupService,
                relationshipMigrationTracker, consistencyLineage)
              filteredFieldsList.++=:(checkResult._2)
              consistencyInfoList.++=:(checkResult._1)
              shouldFilterBuffer.+=:(checkResult._3)
              if (checkResult._3) break()
            })
          }
          (consistencyInfoList.toList, filteredFieldsList.toList, shouldFilterBuffer.head)
        }
      }
      else {
        //logger.trace("-----| TriggerCondition, {}, is false. No need to perform non-periodic check, {} " ,checkDefinition.triggerCondition, checkName)
        (List.empty, List.empty, false)
      }
    }
    catch {
      case e: Exception => {
        logger.trace(s"Error for the pin : ${e.getMessage} for pin ${pin} for check ${checkName} for eventId ${subjectEvent.eventId}")
        logger.trace(s"stack trace for pin ${pin} for check ${checkName} for eventId ${subjectEvent.eventId}", e)
        (List.empty, List.empty, false)
      }
    }
  }

  private[consistencycheck] def evaluateCheckForNonPeriodic(pin: Long,
                                                            checkName: String,
                                                            checkDefinition: GroupedChecks,
                                                            subjectEvent: TaggedCuratedEventWithMap,
                                                            withEvents: List[TaggedCuratedEventWithMap],
                                                            lookupService: OxygenLookup,
                                                            relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                                            consistencyLineage: Lineage): (List[TaggedConsistencyInfo], List[String], Boolean) = {
    // if check expression is defined
    if (checkDefinition.check.isDefined) {
      val checkASTNode: Option[CheckASTNode] = checkDefinition.check
      val astOut = CheckGenerator.traverseASTForNonPeriodic(checkASTNode.get, subjectEvent, withEvents, CheckASTObject(lookupService, relationshipMigrationTracker))
      if (!astOut) {
        val (filteredFields, shouldFilterEvent) = shouldFilterForListOfChecks(checkName, checkDefinition, subjectEvent, lookupService)
        val taggedRes = TaggedConsistencyInfo(pin,
          consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage, checkName, subjectEvent.eventId, checkDefinition.consistencyInfo, shouldFilterEvent)
        (List(taggedRes), filteredFields, shouldFilterEvent)
      } else {
        // logger.trace(s"-----| Did not find inconsistency for check, {}, for eventId: {}",checkName , subjectEvent.eventId)
        (List.empty, List.empty, false)
      }
    }
    // if check processor is defined
    else if (checkDefinition.checkProcessor != null.asInstanceOf[ConsistencyCheckProcessor]) {
      logger.trace(s"-----| Found custom implementation, will use that for check: {}", checkName)
      val check = checkDefinition.checkProcessor
      val res = check.performNonPeriodicChecks(subjectEvent, withEvents)
      if (res._1 == null || res._1.isEmpty) {
        logger.trace("-----| Did not find inconsistency for check, " + checkName + ", for eventId: " + subjectEvent.eventId)
        (List.empty, List.empty, false)
      } else {
        val (filteredFields, shouldFilterEvent) = if (res._2) (checkDefinition.filterFieldsOnError, res._2) else shouldFilterForListOfChecks(checkName, checkDefinition, subjectEvent, lookupService)
        val taggedResList = res._1.map(eachCInfo => TaggedConsistencyInfo(pin,
          consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage,
          checkName, subjectEvent.eventId, eachCInfo, shouldFilterEvent))
        (taggedResList, filteredFields, shouldFilterEvent)
      }
    }
    else {
      // logger.trace(s"-----| Did not find inconsistency for check, {}, for eventId: {}",checkName , subjectEvent.eventId)
      (List.empty, List.empty, false)
    }
  }

  private[consistencycheck] def evaluateCheckForPeriodic(pin: Long,
                                                         checkName: String,
                                                         checkDefinition: GroupedChecks,
                                                         subjectEvent: TaggedCuratedEventWithMap,
                                                         withEvents: List[SyntheticEvent],
                                                         lookupService: OxygenLookup,
                                                         relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                                                         consistencyLineage: Lineage,
                                                         stateCheck: StateCheck): (List[TaggedConsistencyInfo], List[String], Boolean) = {

    if (checkDefinition.check.isDefined) {
      val checkASTNode: Option[CheckASTNode] = checkDefinition.check
      val astOut = CheckGenerator.traverseAST(checkASTNode.get, subjectEvent, withEvents, CheckASTObject(lookupService, relationshipMigrationTracker), stateCheck)
      if (!astOut) {
        val (filteredFields, shouldFilterEvent) = shouldFilterForListOfChecks(checkName, checkDefinition, subjectEvent, lookupService)
        val taggedRes = TaggedConsistencyInfo(pin,
          consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage, checkName, subjectEvent.eventId, checkDefinition.consistencyInfo, shouldFilterEvent)
        (List(taggedRes), filteredFields, shouldFilterEvent)
      }
      else {
        logger.trace("-----| Did not find inconsistency for check, " + checkName + " , for eventId: " + subjectEvent.eventId)
        (List.empty, List.empty, false)
      }
    }
    else if (checkDefinition.checkProcessor != null.asInstanceOf[ConsistencyCheckProcessor]) {
      logger.trace(s"-----| Found custom implementation, will use that for check: {} ", checkName)
      val check = checkDefinition.checkProcessor
      val res = check.performChecks(subjectEvent, withEvents)
      if (res._1 == null || res._1.isEmpty) {
        logger.trace("-----| Did not find inconsistency for check, " + checkName + ", for eventId: " + subjectEvent.eventId)
        (List.empty, List.empty, false)
      }
      else {
        val (filteredFields, shouldFilterEvent) = if (res._2) (checkDefinition.filterFieldsOnError, res._2) else shouldFilterForListOfChecks(checkName, checkDefinition, subjectEvent, lookupService)
        val consistencyInfoList = res._1.map(res => TaggedConsistencyInfo(pin,
          consistencyLineage, subjectEvent.subscriberRelation, subjectEvent.lineage, checkName, subjectEvent.eventId, res, shouldFilterEvent))
        (consistencyInfoList, filteredFields, shouldFilterEvent)
      }
    }
    else (List.empty, List.empty, false)

  }

  private[consistencycheck] def shouldFilterForListOfChecks(checkName: String,
                                                            checkDefinition: GroupedChecks,
                                                            subjectEvent: TaggedCuratedEventWithMap,
                                                            lookupService: OxygenLookup): (List[String], Boolean) = {
    logger.trace("-----| Found inconsistency for check," + checkName + " for eventId: " + subjectEvent.eventId)
    val shouldFilterEventExpression = checkDefinition.shouldFilterEventOnError
    val shouldFilterFieldExpression = checkDefinition.shouldFilterFieldOnError
    //val sp = new ShouldFilterEventParser(lookup)
    val shouldFilterEventOnError = shouldFilterEventExpression //if (shouldFilterEventExpression != null) sp.parseAll(sp.applyFilter, shouldFilterEventExpression).get else false
    val shouldFilterFieldOnError = shouldFilterFieldExpression //if (shouldFilterFieldExpression != null) sp.parseAll(sp.applyFilter, shouldFilterFieldExpression).get else false
    val filterFieldOnError = checkDefinition.filterFieldsOnError
    if (shouldFilterEventOnError) {
      logger.trace(s"-----| this event will be excluded because of inconsistency.")
      (List.empty, shouldFilterEventOnError)
    }
    else if (shouldFilterFieldOnError && filterFieldOnError.nonEmpty) {
      logger.trace(s"-----| this event has inconsistency,  {} will be excluded.", filterFieldOnError.mkString(","))
      (filterFieldOnError, false)
    }
    else {
      logger.trace(s"-----| this event has inconsistency but will not be removed.")
      (List.empty, false)
    }
  }

  private[consistencycheck] def shouldFilter(checkName: String,
                                             checkDefinition: TaggedConsistencyCheckDictionary,
                                             subjectEvent: TaggedCuratedEventWithMap,
                                             lookupService: OxygenLookup): (List[String], Boolean) = {
    logger.trace("-----| Found inconsistency for check," + checkName + " for eventId: " + subjectEvent.eventId)
    val shouldFilterEventExpression = checkDefinition.shouldFilterEventOnError
    val shouldFilterFieldExpression = checkDefinition.shouldFilterFieldOnError
    //val sp = new ShouldFilterEventParser(lookup)
    val shouldFilterEventOnError = shouldFilterEventExpression //if (shouldFilterEventExpression != null) sp.parseAll(sp.applyFilter, shouldFilterEventExpression).get else false
    val shouldFilterFieldOnError = shouldFilterFieldExpression //if (shouldFilterFieldExpression != null) sp.parseAll(sp.applyFilter, shouldFilterFieldExpression).get else false
    val filterFieldOnError = checkDefinition.filterFieldsOnError
    if (shouldFilterEventOnError) {
      logger.trace(s"-----| this event will be excluded because of inconsistency.")
      (List.empty, shouldFilterEventOnError)
    }
    else if (shouldFilterFieldOnError && filterFieldOnError.nonEmpty) {
      logger.trace(s"-----| this event has inconsistency,  {} will be excluded.", filterFieldOnError.mkString(","))
      (filterFieldOnError, false)
    }
    else {
      logger.trace(s"-----| this event has inconsistency but will not be removed.")
      (List.empty, false)
    }
  }

  private[consistencycheck] def getReferenceEvents(checkName: String,
                                                   checkDefinition: TaggedConsistencyCheckDictionary,
                                                   syntheticEvents: List[SyntheticEvent],
                                                   partiallySynthesizedEvents: List[SyntheticEvent],
                                                   subjectEvent: TaggedCuratedEventWithMap,
                                                   currentDateTime: Long,
                                                   referenceCacheMap: Option[java.util.Map[String, List[SyntheticEvent]]] = None): List[SyntheticEvent] = {
    val withEventsExpression = checkDefinition.withReferenceEvents
    logger.trace(s"-----| Using referenceCache: {}", checkDefinition.useReferenceCache)
    logger.trace(s"-----| Evaluating withCondition for check: {}", checkName)

    val refASTNode = checkDefinition.withReferenceEvents

    val filteredSyntheticEventList = if (checkDefinition.useReferenceCache != null)
      if (referenceCacheMap.isDefined) referenceCacheMap.get.get(checkDefinition.useReferenceCache) else List.empty[SyntheticEvent]
    else
      partiallySynthesizedEvents ::: syntheticEvents

    val withEvents = if (checkDefinition.withReferenceEvents != null && checkDefinition.withReferenceEvents != "" && checkDefinition.withReferenceEvents != " " && refASTNode.isDefined)
      if (filteredSyntheticEventList != null && filteredSyntheticEventList.nonEmpty)
        filteredSyntheticEventList.filter(thatEvent => {
          ReferenceGenerator.traverseAST(refASTNode.get, subjectEvent, thatEvent)
        }) else List.empty[SyntheticEvent]
    else
      filteredSyntheticEventList

    logger.trace(s"-----| Count of reference events: {}", withEvents.length)

    withEvents
    //val rf = new ReferenceEventsParser(ParserSyntheticEvent(thatEvent), ParserTaggedCuratedEvent(subjectEvent), currentDateTime)
    //val parseOut = rf.parseAll(rf.applyScope, withEventsExpression).get
    // println(checkDefinition.checkName , ":ref:" , parseOut == astOut)
    //parseOut
  }

  /**
   *
   * @param checkName
   * @param checkDefinition
   * @param taggedCuratedEvents
   * @param subjectEvent
   * @return
   */
  private def getReferenceEventsForNonPeriodic(checkName: String,
                                               checkDefinition: TaggedConsistencyCheckDictionary,
                                               taggedCuratedEvents: Array[TaggedCuratedEventWithMap],
                                               subjectEvent: TaggedCuratedEventWithMap,
                                               currentDateTime: Long,
                                               referenceCacheMap: java.util.HashMap[String, (List[String], java.util.HashMap[String, ListBuffer[TaggedCuratedEventWithMap]])],
                                               referenceEventWindowInMonths: Option[Int]): List[TaggedCuratedEventWithMap] = {
    logger.trace(s"-----| Using referenceCache: {}", checkDefinition.useReferenceCache)
    logger.trace(s"-----| Evaluating withCondition for check: {}", checkName)
    val refASTNode = checkDefinition.withReferenceEvents

    val listFromCache = if (checkDefinition.useReferenceCache != null) {
      val pMap = referenceCacheMap.get(checkDefinition.useReferenceCache)
      val predicateKey = getPredicateKey(pMap._1, subjectEvent)
      pMap._2.get(predicateKey).toList
    }
    else
      taggedCuratedEvents.toList

    //
    val filteredSyntheticEventList = if (referenceEventWindowInMonths.isDefined) {
      val subjectEventDate = Instant.ofEpochMilli(subjectEvent.asOfDate).atZone(ZoneId.of("Etc/UTC"))
      val referenceEventTimeWindowStart: Long = subjectEventDate.minusMonths(referenceEventWindowInMonths.get).toEpochSecond * 1000
      //val x = subjectEvent.asOfPeriod.minus(12)
      listFromCache.filter(_.asOfDate >= referenceEventTimeWindowStart)
    } else listFromCache

    val withEvents = if (checkDefinition.withReferenceEvents != null && checkDefinition.withReferenceEvents != "" && checkDefinition.withReferenceEvents != " " && refASTNode.isDefined) {
      filteredSyntheticEventList.filter(thatEvent =>
        (thatEvent != subjectEvent && ReferenceGenerator.traverseASTForNonPeriodic(refASTNode.get, subjectEvent, thatEvent)))
    }
    else
      filteredSyntheticEventList.filter(_ != subjectEvent)
    logger.trace(s"-----| Count of reference events: {}", withEvents.length)
    withEvents
  }

  private[consistencycheck] def evaluateTriggerCondition(checkDefinition: TaggedConsistencyCheckDictionary, subjectEvent: TaggedCuratedEventWithMap): Boolean = {
    logger.trace(s"-----| Evaluating TriggerCondition for check:{} ", checkDefinition.checkName)
    val triggerASTNode = checkDefinition.triggerCondition
    val astOut = TriggerGenerator.traverseAST(triggerASTNode.get, subjectEvent)
    astOut
  }

  private[oxygen] def readExtensionChecksAndReferenceCache(file: String, lookupService: OxygenLookup, domainDictionary: DomainDictionaryInternal): (Map[String, ReferenceCache], Array[TaggedConsistencyCheckDictionary]) = {
    val source = scala.io.Source.fromURL(getClass.getClassLoader.getResource(file))
    val consistencyCheckDictionaryWithReferenceCacheRead = ScalaJacksonUtil.fromJson(source.mkString.replace("\n", ""), classOf[ConsistencyCheckDictionaryWithReferenceCache]).get
    source.close()
    readExtensionChecksAndReferenceCache(consistencyCheckDictionaryWithReferenceCacheRead, lookupService, domainDictionary)
  }

  private[oxygen] def readExtensionChecksAndReferenceCache(consistencyCheckDictionaryWithReferenceCache: ConsistencyCheckDictionaryWithReferenceCache, lookupService: OxygenLookup, domainDictionary: DomainDictionaryInternal): (Map[String, ReferenceCache], Array[TaggedConsistencyCheckDictionary]) = {
    val consistencyProcessorContext: ConsistencyCheckProcessorContext = ConsistencyCheckProcessorContext(lookupService)

    val consistencyCheckDictionary = if (consistencyCheckDictionaryWithReferenceCache != null) consistencyCheckDictionaryWithReferenceCache.checks else null

    val consistencyDictionary: Array[TaggedConsistencyCheckDictionary] = if (consistencyCheckDictionary == null || consistencyCheckDictionary.isEmpty)
      Array.empty[TaggedConsistencyCheckDictionary]
    else {
      val triggerParser = new TriggerParser(domainDictionary)
      val refParser = new ReferenceParser(domainDictionary)
      val checkParser = new CheckASTParser(domainDictionary)
      val sp = new ShouldFilterEventParser(lookupService)
      val consistencyInfoParser = new ConsistencyInfoParser(lookupService)
      consistencyCheckDictionary.map(x => {
        val checkProcessor = if (x.className != null) Class.forName(x.className.trim).getConstructor(consistencyProcessorContext.getClass).newInstance(consistencyProcessorContext).asInstanceOf[ConsistencyCheckProcessor] else null.asInstanceOf[ConsistencyCheckProcessor]
        val triggerCondition = x.triggerCondition
        val referenceCondition = x.withReferenceEvents
        val checkCondition = x.check
        val checkName = x.checkName
        // Trigger AST
        val triggerASTNode = getTriggerASTNode(triggerParser, triggerCondition, checkName)
        // Reference AST
        val refASTNode = getReferenceASTNode(refParser, referenceCondition, checkName)
        // Check AST
        val checkASTNode = getCheckASTNode(checkParser, checkCondition, checkName)

        val shouldFilterEvent = if (x.shouldFilterEventOnError != null) sp.parseAll(sp.applyFilter, x.shouldFilterEventOnError).get else false
        val shouldFilterField = if (x.shouldFilterFieldOnError != null) sp.parseAll(sp.applyFilter, x.shouldFilterFieldOnError).get else false
        val consistencyInfoParsed =  if (x.consistencyInfo != null && x.consistencyInfo.level !=null) {
          val level = consistencyInfoParser.parseAll(consistencyInfoParser.applyFilter, x.consistencyInfo.level).get
          new ConsistencyInfo(x.consistencyInfo.code, x.consistencyInfo.subCode, level)
        } else x.consistencyInfo

        // Handling grouped checks
        val checksWithInfo = if (x.groupedChecks != null) x.groupedChecks.map(eachCheck => {
          val checkNode = if (eachCheck.check != null) getCheckASTNode(checkParser, eachCheck.check, checkName) else None
          val eachCheckProcessor = if (eachCheck.className != null) Class.forName(eachCheck.className.trim).getConstructor(consistencyProcessorContext.getClass).newInstance(consistencyProcessorContext).asInstanceOf[ConsistencyCheckProcessor] else null.asInstanceOf[ConsistencyCheckProcessor]
          val filterEvent = if (eachCheck.shouldFilterEventOnError != null) sp.parseAll(sp.applyFilter, eachCheck.shouldFilterEventOnError).get else false
          val filterField = if (eachCheck.shouldFilterFieldOnError != null) sp.parseAll(sp.applyFilter, eachCheck.shouldFilterFieldOnError).get else false
          val consistencyInfoAfterParsing = if (eachCheck.consistencyInfo != null && eachCheck.consistencyInfo.level !=null)
           {
            val level = consistencyInfoParser.parseAll(consistencyInfoParser.applyFilter, eachCheck.consistencyInfo.level).get
            new ConsistencyInfo(eachCheck.consistencyInfo.code, eachCheck.consistencyInfo.subCode, level)
          } else eachCheck.consistencyInfo
          GroupedChecks(checkNode, eachCheckProcessor, filterEvent, filterField, eachCheck.filterFieldsOnError, consistencyInfoAfterParsing)
        }) else null
        TaggedConsistencyCheckDictionary(x.checkName,
          checkProcessor,
          x.isNonPeriodic,
          triggerASTNode,
          refASTNode,
          checkASTNode,
          shouldFilterEvent,
          shouldFilterField,
          x.filterFieldsOnError,
          consistencyInfoParsed,
          x.referenceCache,
          x.checkCategory,
          checksWithInfo)

      })
    }


    val referenceCacheList = if (consistencyCheckDictionaryWithReferenceCache != null) consistencyCheckDictionaryWithReferenceCache.referenceCache else null
    val referenceCache = if (referenceCacheList == null || referenceCacheList.isEmpty)
      Map.empty[String, ReferenceCache]
    else {
      val m = referenceCacheList.map(x => x.name -> x)
      m.toMap
    }
    (referenceCache, consistencyDictionary)

  }


  private[oxygen] def readDomainDictionary(file: BufferedSource): DomainDictionaryInternal = DomainDictionaryInternal.readDomainDictionary(file)

  private[oxygen] def readDomainDictionary(file: String): DomainDictionaryInternal = {
    val sourceFile: BufferedSource = scala.io.Source.fromURL(getClass.getClassLoader.getResource(file))
    readDomainDictionary(sourceFile)
  }

  private[oxygen] def readStateAttribute(name: String, stateId: String): StateAttributes = {
    StateAttributes(name, stateId)
  }

  private[oxygen] def getLineageForConsistencyInfo(currentDateTime: Long): Lineage = {
    val consistencyLineage = Lineage(dataSource = "Oxygen",
      receivedDate = currentDateTime,
      fileName = "", subscriber = "Oxygen", fileAsOfDate = 0L)

    consistencyLineage
  }

  def getValueForField(fieldName: String, tce: TaggedCuratedEventWithMap): String = {
    try {
      val tceFieldValue = fieldName match {
        case "subscriberRelation" => tce.subscriberRelation
        case "asOfDate" => tce.asOfPeriod
        case "dataSource" => tce.lineage.dataSource
        case "subscriber" => tce.subscriber
        case _ => tce.curatedRecordJson.get(fieldName)
      }
      tceFieldValue.toString
    }
    catch {
      case ex: Exception => ""
    }
  }

  private[oxygen] def createGlobalReferenceListNonPeriodicOld(referenceCache: Map[String, ReferenceCache], subjectEvent: TaggedCuratedEventWithMap, taggedCuratedList: List[TaggedCuratedEventWithMap]): java.util.HashMap[String, (List[TaggedCuratedEventWithMap])] = {

    val referenceCacheMap = new java.util.HashMap[String, List[TaggedCuratedEventWithMap]]()
    referenceCache.foreach(eachCache => {
      val predicates = eachCache._2.predicates
      val referenceList = taggedCuratedList.filter(e => predicates.foldLeft(true) { (a, c) => getValueForFieldAndCompareNonPeriodic(c, subjectEvent, e) && a })
      referenceCacheMap.put(eachCache._1, referenceList)
    })

    referenceCacheMap
  }

  def getPredicateKey(predicateList: List[String], taggedCurated: TaggedCuratedEventWithMap): String = {
    predicateList.foldLeft("") { (a, c) => getValueForField(c, taggedCurated) + ":" + a }
  }

  private[oxygen] def createGlobalReferenceListNonPeriodic(referenceCache: Map[String, ReferenceCache], taggedCuratedList: List[TaggedCuratedEventWithMap]): java.util.HashMap[String, (List[String], java.util.HashMap[String, ListBuffer[TaggedCuratedEventWithMap]])] = {

    val referenceCacheMap = new java.util.HashMap[String, (List[String], java.util.HashMap[String, ListBuffer[TaggedCuratedEventWithMap]])]
    taggedCuratedList.foreach(event => {
      referenceCache.foreach(eachCache => {
        val predicates = eachCache._2.predicates
        val predicateKey = getPredicateKey(predicates, event)
        val pMap = if (referenceCacheMap.containsKey(eachCache._1)) {
          referenceCacheMap.get(eachCache._1)._2
        }
        else {
          new java.util.HashMap[String, ListBuffer[TaggedCuratedEventWithMap]]
        }
        val curatedList = if (pMap.containsKey(predicateKey))
          pMap.get(predicateKey)
        else
          new ListBuffer[TaggedCuratedEventWithMap]
        event +=: curatedList
        pMap.put(predicateKey, curatedList)
        referenceCacheMap.put(eachCache._1, (eachCache._2.predicates, pMap))
      })
    }
    )
    referenceCacheMap
  }

  def getValueForFieldAndCompareNonPeriodic(fieldName: String, tce: TaggedCuratedEventWithMap, se: TaggedCuratedEventWithMap): Boolean = {
    try {
      val tceFieldValue = fieldName match {
        case "subscriberRelation" => tce.subscriberRelation
        case "asOfDate" => tce.asOfPeriod
        case "dataSource" => tce.lineage.dataSource
        case "subscriber" => tce.subscriber
        case _ => tce.curatedRecordJson.get(fieldName)
      }
      val seFieldValue = fieldName match {
        case "subscriberRelation" => se.subscriberRelation
        case "asOfDate" => se.asOfPeriod
        case "dataSource" => se.lineage.dataSource
        case "subscriber" => se.subscriber
        case _ => se.curatedRecordJson.get(fieldName)
      }
      tceFieldValue == seFieldValue
    } catch {
      case ex: Exception => false
    }
  }

  /**
   * Generate AST Map from the Consistency Check Dictionary
   *
   * @param consistencyCheckDictionary
   * @param domainDictionary
   * @return
   */
  private[oxygen] def generateASTMap(consistencyCheckDictionary: Map[String, ConsistencyCheckDictionary], domainDictionary: DomainDictionaryInternal): Map[String, (Option[TriggerASTNode], Option[ReferenceASTNode], Option[CheckASTNode])] = {
    val triggerParser = new TriggerParser(domainDictionary)
    val refParser = new ReferenceParser(domainDictionary)
    val checkParser = new CheckASTParser(domainDictionary)
    consistencyCheckDictionary.map(x => (x._1, {
      val triggerCondition = x._2.triggerCondition
      val referenceCondition = x._2.withReferenceEvents
      val checkCondition = x._2.check
      // Trigger AST
      val filterTrigger = if (triggerCondition != null && triggerCondition.trim != "")
        Some(triggerCondition.replaceAll("\\.as[a-zA-Z]*", "")) else None
      val tokens = if (filterTrigger.isDefined) Some(TriggerLexer(filterTrigger.get, x._1).orNull) else None
      val triggerASTNode = if (tokens.isDefined && tokens != null) Some(triggerParser(tokens.get, x._1).orNull.node) else None

      // Reference AST
      val filterRef = if (referenceCondition != null && referenceCondition.trim != "")
        Some(referenceCondition.replaceAll("\\.as[a-zA-Z]*", ""))
      else None
      val refTokens = if (filterRef.isDefined) Some(ReferenceLexer(filterRef.get, x._1).getOrElse(List.empty)) else None
      val refASTNode = if (refTokens.isDefined && refTokens != null && refTokens.nonEmpty) Some(refParser(refTokens.get, x._1).orNull.node) else None

      // Check AST
      val filterCheck = if (checkCondition != null && checkCondition.trim != "")
        Some(checkCondition.replaceAll("\\.as[a-zA-Z]*", ""))
      else None
      val checkTokens = if (filterCheck.isDefined) Some(CheckLexer(filterCheck.get, x._2.checkName).getOrElse(List.empty)) else None
      val checkASTNode = if (checkTokens.isDefined && checkTokens != null && checkTokens.nonEmpty) Some(checkParser(checkTokens.get, x._1).orNull.node) else None


      (triggerASTNode, refASTNode, checkASTNode)
    }))
  }


  /**
   *
   * @param triggerParser
   * @param triggerCondition
   * @param checkName
   * @return
   */
  private[oxygen] def getTriggerASTNode(triggerParser: TriggerParser, triggerCondition: String, checkName: String): Option[TriggerASTNode] = {

    // Trigger AST
    val filterTrigger = if (triggerCondition != null && triggerCondition.trim != "")
      Some(triggerCondition.replaceAll("\\.as[a-zA-Z]*", "")) else None
    val tokens = if (filterTrigger.isDefined) Some(TriggerLexer(filterTrigger.get, checkName).orNull) else None
    val triggerASTNode = if (tokens.isDefined && tokens != null) Some(triggerParser(tokens.get, checkName).orNull.node) else None
    triggerASTNode
  }

  /**
   *
   * @param checkParser
   * @param checkCondition
   * @param checkName
   * @return
   */
  private[oxygen] def getCheckASTNode(checkParser: CheckASTParser, checkCondition: String, checkName: String): Option[CheckASTNode] = {
    // Check AST
    val filterCheck = if (checkCondition != null && checkCondition.trim != "")
      Some(checkCondition.replaceAll("\\.as[a-zA-Z]*", ""))
    else None
    val checkTokens = if (filterCheck.isDefined) Some(CheckLexer(filterCheck.get, checkName).getOrElse(List.empty)) else None
    val checkASTNode = if (checkTokens.isDefined && checkTokens != null && checkTokens.nonEmpty) Some(checkParser(checkTokens.get, checkName).orNull.node) else None
    checkASTNode
  }

  private[oxygen] def getReferenceASTNode(refParser: ReferenceParser, referenceCondition: String, checkName: String): Option[ReferenceASTNode] = {

    // Reference AST
    val filterRef = if (referenceCondition != null && referenceCondition.trim != "")
      Some(referenceCondition.replaceAll("\\.as[a-zA-Z]*", ""))
    else None
    val refTokens = if (filterRef.isDefined) Some(ReferenceLexer(filterRef.get, checkName).getOrElse(List.empty)) else None
    val refASTNode = if (refTokens.isDefined && refTokens != null && refTokens.nonEmpty) Some(refParser(refTokens.get, checkName).orNull.node) else None
    refASTNode

  }

  private[consistencycheck] def getSRKey(sub: String, rel: String): Int = {
    (sub + ":" + rel).hashCode
  }

  private[oxygen] def groupEventsBySR(events: Array[TaggedCuratedEventWithMap]): mutable.HashMap[Int, ArrayBuffer[TaggedCuratedEventWithMap]] = {
    val eventsGroupedBySR = mutable.HashMap[Int, ArrayBuffer[TaggedCuratedEventWithMap]]()
    events.foreach(tce => {
      val key = getSRKey(tce.subscriber, tce.subscriberRelation)
      val eventsForKey = eventsGroupedBySR.getOrElseUpdate(key, ArrayBuffer[TaggedCuratedEventWithMap]())
      eventsForKey += tce
    })
    eventsGroupedBySR
  }

  /**
   * Filter all the events for a period that were received prior to a deleted events. e.g.:
   *
   * receivedDate  S+R      asOfDate    events
   * 20180315      S1+R1    20180308    e1
   * 20180429      S1+R1    20180308    e2
   * 20180515      S1+R1    20180308    e3 - isInferredAsDeleted = True
   * 20180529      S1+R1    20180308    e4
   *
   * In the above case events e1, e2, and e3 will be filtered out.
   *
   * @param eventsForAPeriod
   * @return
   */
  private[oxygen] def filterDeletedEvents(eventsForAPeriod: Array[TaggedCuratedEventWithMap], pin: Long, shouldReportAllDeletedEvents: Boolean, currDateTime: Long): (Array[TaggedCuratedEventWithMap], ListBuffer[TaggedConsistencyInfo]) = {
    val eventsGroupedBySR = groupEventsBySR(eventsForAPeriod)
    val consistencyInfo1 = new ConsistencyInfo(SynthesisConstants.INFERRED_DELETE_SYNTHESYS, SynthesisConstants.INFERRED_DELETE_SYNTHESYS_SUBCODE, "1")

    val consistencyLineage = ConsistencyCheckOps.getLineageForConsistencyInfo(currDateTime)
    val filteredEvents = ArrayBuffer[TaggedCuratedEventWithMap]()
    val inferredDeletedConsistencyInfo = ListBuffer[TaggedConsistencyInfo]()

    import util.control.Breaks._
    eventsGroupedBySR.foreach(v => {
      // Sort descending
      val sortedEvents = v._2.sortBy(e => (-e.asOfDate, -e.lineage.receivedDate))
      breakable {
        for (e <- 0 to sortedEvents.size - 1) {
          // if event is not deleted add to the list.
          // Do not add any events with inferredDeleted true and any event before that
          if (!TaggedCuratedEventWithMap.isInferredDeleted(sortedEvents(e))) filteredEvents += sortedEvents(e)
          else {
            if (shouldReportAllDeletedEvents) {
              for (errorIndex <- e to sortedEvents.size - 1) {
                inferredDeletedConsistencyInfo += TaggedConsistencyInfo(pin,
                  consistencyLineage, sortedEvents(errorIndex).subscriberRelation, sortedEvents(errorIndex).lineage,
                  SynthesisConstants.INFERRED_DELETE_CHECKNAME, sortedEvents(errorIndex).eventId, consistencyInfo1, true)
              }
            }
            else {
              inferredDeletedConsistencyInfo += TaggedConsistencyInfo(pin,
                consistencyLineage, sortedEvents(e).subscriberRelation, sortedEvents(e).lineage,
                SynthesisConstants.INFERRED_DELETE_CHECKNAME, sortedEvents(e).eventId, consistencyInfo1, true)
            }
            break
          }
        }
      }
    })

    (filteredEvents.toArray, inferredDeletedConsistencyInfo)
  }

  private[oxygen] def createRelationshipMigrationTracker(taggedCuratedEvents: Array[TaggedCuratedEventWithMap], migrationProcessor: RelationshipMigrationProcessor,migrationSubscriberLoggingEnabled :Boolean): (mutable.HashMap[Int, RelationshipMigrationTrackingField], mutable.HashMap[Int, RelationshipMigrationTrackingField],mutable.HashMap[Int, RelationshipMigrationTrackingLogging]) = {

    val migrationTrackerWithSource = mutable.HashMap[Int, RelationshipMigrationTrackingField]()
    val migrationTrackerWithDest = mutable.HashMap[Int, RelationshipMigrationTrackingField]()
    val migrationTrackerForLogging = mutable.HashMap[Int, RelationshipMigrationTrackingLogging]()
    val listMigration =  ListBuffer[RelationshipMigrationTrackingLogging]()
    // Filter all events which require migration
    if (migrationProcessor != null) {
      val migrationEvents = taggedCuratedEvents.filter(e => migrationProcessor.requiresMigration(e.curatedRecordJson))
      migrationEvents.foreach(migrationEvent => {
        //get destination
        val (destinationSubscriber, destinationSubscriberRelation) = migrationProcessor.getDestination(migrationEvent.curatedRecordJson)
        val destinationSRKey = ConsistencyCheckOps.getSRKey(destinationSubscriber, destinationSubscriberRelation)
        val sourceSRKey = ConsistencyCheckOps.getSRKey(migrationEvent.subscriber, migrationEvent.subscriberRelation)
        migrationTrackerWithSource.put(sourceSRKey, RelationshipMigrationTrackingField(destinationSubscriber, destinationSubscriberRelation, migrationEvent.asOfDate))
        migrationTrackerWithDest.put(destinationSRKey, RelationshipMigrationTrackingField(migrationEvent.subscriber, migrationEvent.subscriberRelation, migrationEvent.asOfDate))
        createRelationshipMigrationLogging(migrationSubscriberLoggingEnabled,migrationTrackerForLogging,migrationEvent.subscriber,migrationEvent.subscriberRelation,destinationSubscriber,destinationSubscriberRelation,migrationEvent.asOfDate)
      })
      (migrationTrackerWithSource, migrationTrackerWithDest,migrationTrackerForLogging)
    }
    else {
      (mutable.HashMap.empty[Int, RelationshipMigrationTrackingField], mutable.HashMap.empty[Int, RelationshipMigrationTrackingField],mutable.HashMap.empty[Int, RelationshipMigrationTrackingLogging])
    }
  }

  private [oxygen] def createRelationshipMigrationLogging (migrationSubscriberLoggingEnabled :Boolean, migrationTrackerForLogging: mutable.HashMap[Int, RelationshipMigrationTrackingLogging], subscriber : String ,subscriberRelation : String,destinationSubscriber : String ,destinationSubscriberRelation : String, asOfDate: Long): Unit = {
     if (migrationSubscriberLoggingEnabled) {
       val createKeyHash = (subscriber + ":" + subscriberRelation + ":" +destinationSubscriber + ":" + destinationSubscriberRelation+":"+ asOfDate ).hashCode
       if (!migrationTrackerForLogging.contains(createKeyHash)) {
         migrationTrackerForLogging.put(createKeyHash, new RelationshipMigrationTrackingLogging(subscriber,subscriberRelation,destinationSubscriber,destinationSubscriberRelation,asOfDate))
       }
     }
  }



  /**
   *
   * @param checks
   * @param categoryList
   * @param numberOfAccountsInHC
   * @return
   */
  @tailrec
  private[oxygen] def getFilteredChecks(checks: Array[TaggedConsistencyCheckDictionary], categoryList: List[CheckCategoryElement], numberOfAccountsInHC: Long): Array[TaggedConsistencyCheckDictionary] = {
    if (categoryList.isEmpty)
      checks
    else {
      val head = categoryList.head
      val tail = categoryList.tail
      if (head.categoryThreshold <= numberOfAccountsInHC)
        getFilteredChecks(checks.filter(_.checkCategory != head.categoryName), tail, numberOfAccountsInHC)
      else
        getFilteredChecks(checks, tail, numberOfAccountsInHC)
    }

  }


  def timeReporter[R](block: => R,
                      taskName: String = "process",
                      reporter: (String, Long) => Unit = (name, elapsedNanos) => {
                        logger.trace(s"${name} elapsed time ${elapsedNanos} ms")
                      }): R = {
    val t0 = System.currentTimeMillis()
    val result = block
    val t1 = System.currentTimeMillis()
    reporter(taskName, t1 - t0)
    result
  }


}
