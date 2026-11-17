package com.experian.edf.oxygen.pinning.domain

import com.experian.edf.oxygen.domain.{TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedWritableEvent}

private[oxygen] case class HeterogeneousCollectionJson(pin: Long, taggedCuratedEvents: Array[TaggedCuratedEvent]) extends Serializable

case class HeterogeneousCollection(pin: Long, taggedCuratedEvents: Array[TaggedCuratedEventWithMap],
                                   tags: List[String] = List.empty[String]) extends Serializable

case class HeterogeneousWritableCollection(pin: Long, events: Array[TaggedWritableEvent]) extends Serializable

object HeterogeneousWritableCollection {
  def toHeterogeneousCollection(collecton: HeterogeneousWritableCollection): HeterogeneousCollection = HeterogeneousCollection(collecton.pin, collecton.events.map(TaggedWritableEvent.toTaggedCuratedEvent))

  def fromHeterogeneousCollection(collecton: HeterogeneousCollection): HeterogeneousWritableCollection = HeterogeneousWritableCollection(collecton.pin, collecton.taggedCuratedEvents.map(TaggedWritableEvent.fromTaggedCuratedEvent))
}

