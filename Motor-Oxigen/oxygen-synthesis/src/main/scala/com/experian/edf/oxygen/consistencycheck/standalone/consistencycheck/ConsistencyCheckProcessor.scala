package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import com.experian.edf.oxygen.domain.{ConsistencyInfo, SyntheticEvent, TaggedCuratedEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.domain.processors.ConsistencyCheckProcessorContext
import com.experian.edf.oxygen.domain.processors.OxygenProcessor
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.utils.JsonUtils

abstract class ConsistencyCheckProcessor(consistencyCheckProcessorContext: ConsistencyCheckProcessorContext) extends OxygenProcessor {

 /* def this(context: ConsistencyCheckProcessorContext) = {
    this(context.lookup)
  }*/

  override def lookupService: OxygenLookup = consistencyCheckProcessorContext.lookup

  def performChecks(event: TaggedCuratedEventWithMap, syntheticEvents: List[SyntheticEvent]): (List[ConsistencyInfo], Boolean) = {
    val res = performCheck(event, syntheticEvents)
    if (res != null) {
      (List(res), false)
    }
    else {
      (List.empty[ConsistencyInfo], false)
    }
  }

  def performNonPeriodicChecks(event: TaggedCuratedEventWithMap, withEvents: List[TaggedCuratedEventWithMap]): (List[ConsistencyInfo], Boolean) = {
    val res = performNonPeriodicCheck(event, withEvents)
    if (res != null) {
      (List(res), false)
    }
    else {
      (List.empty[ConsistencyInfo], false)
    }
  }

  @deprecated
  def performCheck(event: TaggedCuratedEventWithMap, syntheticEvents: List[SyntheticEvent]): (ConsistencyInfo) = {
    val taggedCuratedEvent = JsonUtils.fromJson(JsonUtils.toJson[TaggedCuratedEventWithMap](event), classOf[TaggedCuratedEvent])
    performCheck(taggedCuratedEvent, syntheticEvents)
  }

  @deprecated
  def performNonPeriodicCheck(event: TaggedCuratedEventWithMap, withEvents: List[TaggedCuratedEventWithMap]): ConsistencyInfo = {
    val taggedCuratedEvent = JsonUtils.fromJson(JsonUtils.toJson[TaggedCuratedEventWithMap](event), classOf[TaggedCuratedEvent])
    val taggedCuratedWithEvents = withEvents.map(x => JsonUtils.fromJson(JsonUtils.toJson[TaggedCuratedEventWithMap](x), classOf[TaggedCuratedEvent]))
    performNonPeriodicCheck(taggedCuratedEvent, taggedCuratedWithEvents)
  }

  @deprecated
  def performCheck(event: TaggedCuratedEvent, syntheticEvents: List[SyntheticEvent]): ConsistencyInfo = ???

  @deprecated
  def performNonPeriodicCheck(event: TaggedCuratedEvent, withEvents: List[TaggedCuratedEvent]): ConsistencyInfo = ???
}