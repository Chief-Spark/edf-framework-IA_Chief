package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}

import scala.annotation.tailrec


/**
  * Synthetic events represent the netted out effect of all applicable events for a subscriber relationship
  * from the same data source for a period
  *
  * For example:
  *
  * Monthly update received from bank on June 25th: currentBalance = 200, delinquentBalance = 1000
  * Correction event on June 27th: delinquentBalance = 500
  * Synthetic event for June (assuming the event has a monthly frequency): currentBalance = 200, delinquentBalance = 500
  *
  * @param events Events for a period and a subscriber relation.
  *              Note that events must belong to the same period otherwise the netted effect of synthesis will not
  *              yield the right result.
  *              EVENTS MUST BE ALREADY SORTED ON RECEIVED DATE
  *
  * @param domainDict
  * @param boundaryEvent if supplied the netting applies till the boundary event
  */

class EventSynthesizer(events: List[TaggedCuratedEventWithMap],
                       domainDict: Array[DictionaryFieldInternal],
                       euc: EUC,
                       lap: LAP,
                       boundaryEvent: TaggedCuratedEventWithMap = null) extends Serializable {

  // sort on the received date
  //private lazy val sortedEvents = events.sortBy(e => e.lineage.receivedDate)
  private val event = events.last
  private val ds = event.lineage.dataSource
  private val sub = event.subscriber
  private val relation = event.subscriberRelation
  private val frequency = event.minFrequency
  private val parentEventIds = events.map(e => e.eventId)
  private val period = event.getPeriod
  val firstEventDate = euc.getFirstEventDate(ds, sub, relation)

  private val synthesizedCuratedRecordJson =  new java.util.HashMap[String,Any]()
  synthesize(events)

  @tailrec
  private def synthesize(rem: List[TaggedCuratedEventWithMap]): Unit = {
    if (rem.isEmpty || (boundaryEvent != null && rem.head.eventId == boundaryEvent.eventId)) {
      return
    }
    //if (rem.length == 1 || (boundaryEvent != null && rem.head.eventId == boundaryEvent.eventId)) {
    //  val head = rem.head
    //  overrideWithNewProp(head.curatedRecordJson, head.asOfDate)
    //}
    else {
      val head = rem.head
      overrideWithNewProp(head.curatedRecordJson, head.asOfDate)
      synthesize(rem.tail)
    }
  }

  private def overrideWithNewProp(curatedRec: java.util.Map[String,Any], asOfDate: Long) = {

    domainDict.foreach(f => {
      // If EUC, then get the EUC value
      val p = if (f.handler == DomainDictionaryInternal.EUCFieldHandler) {
        val p = euc.get(f.name, ds, sub, relation).getOrElse(null)
        if (p != null)
          p.value
        else null
      }
      else curatedRec.get(f.name)

      if (p != null) {
        synthesizedCuratedRecordJson.put(f.name, p)
      } else {
        // if value is not present,  if it is LAP, return LAP value
        if (f.handler == DomainDictionaryInternal.LAPFieldHandler) {
          val p = lap.get(f.name, ds, sub, relation).getOrElse(null)
          if (p != null && p.asOfDate < asOfDate)
            synthesizedCuratedRecordJson.put(f.name, p.value)
        }
      }
    })
  }

  private val syntheticEvent = SyntheticEvent(ds, sub, relation, frequency, period, parentEventIds.toArray, synthesizedCuratedRecordJson)
  def get: SyntheticEvent = syntheticEvent
}

object IncrementalEventSynthesizer {

  def synthesize(tce: TaggedCuratedEventWithMap,
                 se: SyntheticEvent,
                 domainDict: Array[DictionaryFieldInternal],
                 euc: EUC,
                 lap: LAP,
                 fieldToExclude: String = ""): SyntheticEvent = {
    val ds = tce.lineage.dataSource
    val sub = tce.subscriber
    val relation = tce.subscriberRelation
    val frequency = tce.minFrequency
    val period = tce.getPeriod
    val mapBuffer = new java.util.HashMap[String,Any]()
    val synthesizedCuratedRecordJson = if (se == null) mapBuffer
      else se.curatedRecordJson
    val firstEventDate = euc.getFirstEventDate(ds, sub, relation)

    domainDict.foreach(f => {
      if(f.name != fieldToExclude) {
        // If EUC, then get the EUC value
        val value = if (f.handler == DomainDictionaryInternal.EUCFieldHandler) {
          val p = euc.get(f.name, ds, sub, relation).orNull
          if (p != null)
            p.value
          else null
        }
        //  If LAP, get the lAP value
       else if (f.handler == DomainDictionaryInternal.LAPFieldHandler) {
          val p = lap.get(f.name, ds, sub, relation).orNull
          if (p != null && p.asOfDate <= tce.asOfDate)
            p.value
          else null
        }
        // else customized synthesis for LFP fields
        else if (f.handler != DomainDictionaryInternal.EUCFieldHandler && f.handler != DomainDictionaryInternal.LAPFieldHandler && f.synthesizer.isDefined) {
          val subjectEventFieldValue = tce.curatedRecordJson.get(f.name)
          val syntheticEventFieldValue = if (se != null) se.curatedRecordJson.get(f.name) else null
          val synthesizer = f.synthesizer.get
          val synthesizedFieldValue = synthesizer.performFieldSynthesis(f.name, Option(subjectEventFieldValue), Option(syntheticEventFieldValue) )
          synthesizedFieldValue
        }
          // else just get the latest value
        else {
          if (tce.curatedRecordJson.containsKey(f.name)) tce.curatedRecordJson.get(f.name) else null
        }

        if (value != null ) {
          synthesizedCuratedRecordJson.put(f.name, value)
        }
      }
    })

    synthesizedCuratedRecordJson.put( "firstEventDate",firstEventDate)

    val eventIds = if (se == null)
      List(tce.eventId)
    else
      tce.eventId :: se.parentEventIds.toList

    new SyntheticEvent("Oxygen", sub, relation, frequency, period, eventIds.toArray, synthesizedCuratedRecordJson)
  }


}
