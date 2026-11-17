package com.experian.edf.oxygen.consistencycheck.standalone.utils

import java.util.concurrent.ConcurrentHashMap
import com.experian.edf.oxygen.domain.{TaggedCuratedEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.global.utils.Utils
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.StateCheck
import com.experian.edf.oxygen.domain.internal.{Period, PeriodUnique}
import com.google.gson.JsonElement

import scala.annotation.tailrec
import scala.collection.mutable.{HashMap, ListBuffer}


object HCUtil {

  /**
   * Returns the earliest value until the value was corrected by a correction event.
   *
   * @param fieldName
   * @param events list of events for a datasource, subscriber, and subscriberRelation
   * @return JSON Element
   */
  private[oxygen] def getEarliestUnlessCorrectedValue(fieldName: String, events: List[TaggedCuratedEvent]): Option[JsonElement] = {

    val correctedValue = getMostRecentValue(fieldName + "_corrected", events)
    if (correctedValue == null)
      getEarliestValue(fieldName, events)
    else
      correctedValue
  }

  private[oxygen] def getMostRecentValue(fieldName: String, events: List[TaggedCuratedEvent]): Option[JsonElement] = {

    @tailrec
    def getMostRecentValueInternal(thatEvents: List[TaggedCuratedEvent], currValue: JsonElement, till: Long): Option[JsonElement] = {
      if (thatEvents == null || thatEvents.isEmpty) {
        Some(currValue)
      }
      else {
        val head = thatEvents.head
        val mostRecentValue = head.curatedRecordJson.get(fieldName)
        if (mostRecentValue == null)
          getMostRecentValueInternal(thatEvents.tail, currValue, till)
        else if (head.asOfDate > till)
          getMostRecentValueInternal(thatEvents.tail, mostRecentValue, head.asOfDate)
        else
          getMostRecentValueInternal(thatEvents.tail, currValue, till)
      }
    }

    getMostRecentValueInternal(events, null.asInstanceOf[JsonElement], Long.MinValue)
  }

  private[oxygen] def getEarliestValue(fieldName: String, events: List[TaggedCuratedEvent]): Option[JsonElement] = {

    @tailrec
    def getEarliestValueInternal(thatEvents: List[TaggedCuratedEvent], currValue: JsonElement, till: Long): Option[JsonElement] = {
      if (thatEvents == null || thatEvents.isEmpty) {
        Some(currValue)
      } else {
        val head = thatEvents.head
        val earliestValue = head.curatedRecordJson.get(fieldName)
        if (earliestValue == null)
          getEarliestValueInternal(thatEvents.tail, currValue, till)
        else if (head.asOfDate < till)
          getEarliestValueInternal(thatEvents.tail, earliestValue, head.asOfDate)
        else
          getEarliestValueInternal(thatEvents.tail, currValue, till)
      }
    }

    getEarliestValueInternal(events, null.asInstanceOf[JsonElement], Long.MaxValue)
  }

  private[oxygen] def getTceWithVoidIndicator(tce: TaggedCuratedEventWithMap, eachTypeTimeMap: ConcurrentHashMap[String, Long], dateType: String): TaggedCuratedEventWithMap = {

    val fileLevelLatestTime = if (tce.fileTag != null && eachTypeTimeMap.containsKey(tce.fileTag)) eachTypeTimeMap.get(tce.fileTag) else -1L

    val eventTime = TaggedCuratedEventWithMap.getValueForField(dateType, tce).getOrElse(0L).toString.toLong
    if (fileLevelLatestTime != -1L) {
      val shouldBeVoid = if (eventTime >= fileLevelLatestTime) false else true
      tce.curatedRecordJson.put(SynthesisConstants.VOID_INDICATOR, shouldBeVoid)
    }
    tce

  }

  /**
   * List of events by Period.
   *
   * @param events
   * @return
   */
  private[oxygen] def groupEventsByPeriod(events: Array[TaggedCuratedEventWithMap], stateCheck: StateCheck, stateCheckEnabled: Boolean, eachTypeTimeMap: ConcurrentHashMap[String, Long], checkFileTagEnabled: Boolean, dateType: String): (Array[(Period, ListBuffer[TaggedCuratedEventWithMap])], StateCheck) = {
    val eventsByPeriod = HashMap[Period, ListBuffer[TaggedCuratedEventWithMap]]()
    events.map(e => {

      if (stateCheckEnabled) {
        // TODO get name that is only necessary for this call
        val stateName = stateCheck.getStateCheckFields.map(each => each._1)
        stateName.map(eachName => {
          if (Utils.checkValueExistsAndNonNull(e.curatedRecordJson,eachName._1)) {
            stateCheck.update(e)
          }
        })
      }

      val bothUniqueNonUniqueGroup = if (e.getPeriod.unit != PeriodUnique) {
        val newGroupNonUnique = ListBuffer[TaggedCuratedEventWithMap]()

        val p = e.getPeriod
        eventsByPeriod.getOrElse(p, {
          eventsByPeriod.put(p, newGroupNonUnique)
          newGroupNonUnique
        })
      }
      else {
        val newGroupUnique = ListBuffer[TaggedCuratedEventWithMap]()

        val uniquePeriod = Period.uniquePeriod(e.eventId, e.getPeriod.epochDateTime)
        eventsByPeriod.put(uniquePeriod, newGroupUnique)
        newGroupUnique
      }
      if (checkFileTagEnabled) {
        bothUniqueNonUniqueGroup += {
          val voidIndicatorAddedTce = getTceWithVoidIndicator(e, eachTypeTimeMap, dateType)
          voidIndicatorAddedTce
        }
      }
      else {
        bothUniqueNonUniqueGroup += e
      }

    })
    // if stateCheck is enabled create the table after all events have been added to the list
    if (stateCheckEnabled) stateCheck.createStateEventTable()
    // sorting the grouped events by period
    (eventsByPeriod.toArray.sortBy(_._1)(Ordering[Period]), stateCheck)
  }
}

// TODO: quarter is 20174 - how will state calculation interpret it