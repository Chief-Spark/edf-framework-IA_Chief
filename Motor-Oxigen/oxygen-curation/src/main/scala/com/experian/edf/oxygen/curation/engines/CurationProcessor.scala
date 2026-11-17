package com.experian.edf.oxygen.curation.engines

import com.experian.edf.oxygen.curation.framework.CurationService
import com.experian.edf.oxygen.curation.domain.ValidatedEvent
import com.experian.edf.oxygen.domain.{ICurationProcessor, TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Payload}
import com.experian.edf.oxygen.domain.InferredAsDeleted
import com.experian.edf.oxygen.domain.processors.{CurationProcessorContext, OxygenProcessor}
import com.experian.edf.oxygen.lookup.OxygenLookup

/**
  * The curation processor is the interface to be implemented
  * by the bureau implementation. This processor helps interpret
  * accepted events and curates them.
  *
  */
abstract class CurationProcessor(curationProcessorContext: CurationProcessorContext) extends OxygenProcessor with ICurationProcessor{

  override def lookupService: OxygenLookup = curationProcessorContext.lookup

  /**
    * Implementation specific method: To create quality scored event
    *
    * @param acceptedRecord : AcceptedEvent
    * @return : AcceptedEvent
    */
  @deprecated
  def readAcceptedEvent(acceptedRecord: String): ValidatedEvent = ???

  /**
    * Implementation specific method: To curate the event and return a curated event
    *
    * @param taggedAcceptedEvent AcceptedEvent : The quality scored (accepted) event which needs to be curated
    * @return CuratedEvent
    */
  def curateEvent(taggedAcceptedEvent: TaggedValidatedEvent): CuratedEvent

  // The type of event that the processors generates.  Event Type is used to find the appropriate class that process this event.
  @deprecated
  val outputEventType: Int = 0

  /**
    * Curation Service
    */
  val curationService: CurationService = null.asInstanceOf[CurationService]

  /**
    * The framework associates the payload with the event.  This payload is available in subsequent stages
    * of the pipeline.
    *
    * @param event
    * @return Payload. Payload is a Map of Key (of type String) and Value (of type String).
    *
    */
  @deprecated
  def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = Payload.empty


  def subscriberRelation(taggedAcceptedEvent: TaggedValidatedEvent): String = taggedAcceptedEvent.subscriberRelation

  /**
    * Start date of relationship (e.g. account opening date), if any
    */
  @deprecated
  def subscriberRelationStartDate(taggedAcceptedEvent: TaggedValidatedEvent): Long = -1L

  /**
    * End date of relationship (e.g. account close/expiry date), if any
    */
  @deprecated
  def subscriberRelationEndDate(taggedAcceptedEvent: TaggedValidatedEvent): Long = -1L

  /**
    * Any internal data oxygen use
    */
  def inferredAsDeleted(taggedAcceptedEvent: TaggedValidatedEvent): InferredAsDeleted = taggedAcceptedEvent.inferredAsDeleted


  def enrichLoggingTaggedCuratedEventWithMap(taggedCuratedEventWithMap: TaggedCuratedEventWithMap):String = taggedCuratedEventWithMap.toString

}

