package com.experian.edf.oxygen.curation

//class CurationInferProcessor(context: CurationProcessorContext) extends CurationProcessor(context: CurationProcessorContext) {
//
//  override def curateEvent(taggedValidatedEvent: TaggedValidatedEvent): CuratedEvent = {
//
//    val event = new CuratedEvent(taggedValidatedEvent.validatedRecordJson.toString)
//    event
//  }
//
////  class CuratedEvent(curatedRecord : String) extends RawEvent(curatedRecord) {
////
////    // Implementation specific method: Create myself from the accepted record
////    override def read(curatedRecord: String): CuratedEvent
////
////    // Implementation specific method: Write myself out as a string
////    override def write: String
////
////  }
//  /**
//    * Implementation specific method: To create quality scored event
//    *
//    * @param acceptedRecord : AcceptedEvent
//    * @return : AcceptedEvent
//    */
//  override def readAcceptedEvent(acceptedRecord: String): ValidatedEvent = {
//    new CurationEventDefaultEvent(acceptedRecord)
//  }
//
//  override val outputEventType: Int = -1
//
//  override def subscriberRelation(taggedAcceptedEvent: TaggedValidatedEvent): String = taggedAcceptedEvent.subscriberRelation
//}
