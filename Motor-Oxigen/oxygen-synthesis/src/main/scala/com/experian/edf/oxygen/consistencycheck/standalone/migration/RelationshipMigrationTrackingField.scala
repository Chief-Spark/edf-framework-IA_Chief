package com.experian.edf.oxygen.consistencycheck.standalone.migration

import scala.collection.mutable


object RelationshipMigrationTracker extends Serializable {
  def empty: RelationshipMigrationTracker = RelationshipMigrationTracker(mutable.HashMap.empty[Int, RelationshipMigrationTrackingField])
}

case class RelationshipMigrationTracker(migrationTracker: mutable.HashMap[Int,RelationshipMigrationTrackingField]) extends Serializable


case class RelationshipMigrationTrackingField(subscriber : String, subscriberRelation: String, migrationAsOfDate: Long) extends Serializable//, migrationReceivedDate: Long)
