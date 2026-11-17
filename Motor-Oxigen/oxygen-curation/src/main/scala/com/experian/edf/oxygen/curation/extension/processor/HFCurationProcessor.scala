package com.experian.edf.oxygen.curation.extension.processor

import com.experian.edf.oxygen.curation.engines.CurationProcessor
import com.experian.edf.oxygen.curation.extension.service.CurationRIService
import com.experian.edf.oxygen.curation.extension.event.{CreditAcceptedEvent, HFCuratedEvent}
import com.experian.edf.oxygen.curation.domain.ValidatedEvent
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Payload}
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext

class HFCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context: CurationProcessorContext) {
  /**
    * Implementation specific method: To create quality scored event
    *
    * @param acceptedRecord AcceptedEvent:
    * @return AcceptedEvent
    */
  override def readAcceptedEvent(acceptedRecord: String): ValidatedEvent = {
    new CreditAcceptedEvent(acceptedRecord)
  }

  /**
    * Implementation specific method: To curate the event and return a curated event
    *
    * @param taggedValidatedEvent TaggedAcceptedEvent : The tagged accepted event which needs to be curated
    * @return CuratedEvent
    */
  override def curateEvent(taggedValidatedEvent: TaggedValidatedEvent): CuratedEvent = {

    new HFCuratedEvent(taggedValidatedEvent.validatedRecordJson)

  }

  override def subscriberRelation(taggedValidatedEvent: TaggedValidatedEvent): String = {
    taggedValidatedEvent.subscriberRelation
  }

  /*
  override def createContextIndex(event: TaggedAcceptedEvent): ContextIndex = {
    val creditAcceptedEvent = new CreditAcceptedEventJson(event.acceptedRecord).creditEvent

    val lookups = mutable.Map[String, Array[String]]()
    lookups += ("AccountNumber" -> Array(event.lineage.dataSource,
      creditAcceptedEvent.accountNumber))
    new ContextIndex(lookups.toMap)
  } */

  override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = event.payload

  override val outputEventType: Int = 1
  /**
    * Curation Service
    */
  override val curationService: CurationRIService = new CurationRIService()
}
