package com.experian.edf.oxygen.consistencycheck.standalone.migration

case class RelationshipMigrationTrackingLogging(subscriber : String = "", subscriberRelation: String = "", destinationSubscriber : String = "", destinationSubscriberRelation: String= "" ,  asOfDate: Long = 0) extends Serializable {

}
