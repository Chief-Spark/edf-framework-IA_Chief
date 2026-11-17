package com.experian.edf.oxygen.curation.engines

import com.experian.edf.oxygen.curation.engines.event.InferredDeletedCuratedEvent
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.curation.domain.{ValidatedEvent}
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.CuratedEvent

class InferredDeletedCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context: CurationProcessorContext) {

  override def curateEvent(taggedValidatedEvent: TaggedValidatedEvent): CuratedEvent = {
    new InferredDeletedCuratedEvent(taggedValidatedEvent.inferredAsDeleted.records)
  }
  override def readAcceptedEvent(acceptedRecord: String): ValidatedEvent = throw new NotImplementedError("Not Implemented")

//  override val outputEventType: Int = -1
//  override val outputEventType(: Int = -1

  override def subscriberRelation(taggedValidatedEvent: TaggedValidatedEvent): String = taggedValidatedEvent.subscriberRelation
}
