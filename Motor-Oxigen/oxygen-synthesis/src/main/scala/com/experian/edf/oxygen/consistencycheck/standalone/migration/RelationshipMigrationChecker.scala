package com.experian.edf.oxygen.consistencycheck.standalone.migration

import com.experian.edf.oxygen.domain.{MigrationHistory, SyntheticEvent}
import com.experian.edf.oxygen.consistencycheck.domain.{EUC, LAP}
import com.experian.edf.oxygen.consistencycheck.standalone.custom.CustomRuleChecker
import com.experian.edf.oxygen.utils.JsonUtils
import com.google.gson.JsonObject

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Performs relationship migration.
 *
 */
object RelationshipMigrationChecker {
  /**
   * Performs the Relationship migration.
   *
   * @param events
   * @param migrationProcessor
   * @return
   */
  private[oxygen] def performRelationshipMigration(events: List[SyntheticEvent], eventsForMigration: List[SyntheticEvent],
                                                   migrationProcessor: RelationshipMigrationProcessor, euc: EUC, lap: LAP,
                                                   relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField]): List[SyntheticEvent] = {
    performRelationshipMigration2(events, eventsForMigration, migrationProcessor, euc, lap, relationshipMigrationTracker)._1

  }

  /**
   *
   * @return
   * - Position 1 > Complete set of new Synthesised Events
   * - Position 2 > Events which are migrated
   * - Position 3 > Events removed because of migration
   */
  private[oxygen] def performRelationshipMigration2(events: List[SyntheticEvent], eventsForMigration: List[SyntheticEvent],
                                                    migrationProcessor: RelationshipMigrationProcessor, euc: EUC, lap: LAP,
                                                    relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField]): (List[SyntheticEvent], List[SyntheticEvent], List[SyntheticEvent]) = {

    val sortedEvents = events.sortBy(x => x.asOfPeriod)
    //get all the migrationEvents sorted on period (lowest first)
    val migrationEvents = eventsForMigration.filter(e => migrationProcessor.requiresMigration(e.curatedRecordJson)).sortBy(x => x.asOfPeriod.epochDateTime)
    /* // TODO: is this correct way & optimal?
     val migrationEvents = eventsForMigration.filter(e => {
       val key = getSRKey(e.subscriber, e.subscriberRelation)
       relationshipMigrationTracker.contains(key) && Period.monthlyPeriodFromEpochDateTime(relationshipMigrationTracker(key).migrationAsOfDate).id == e.asOfPeriod.id
     }).sortBy(x => x.asOfPeriod.epochDateTime)*/

    if (!migrationEvents.isEmpty) {
      //Create a hash table of S + SR
      val eventsBySR = buildEventHashtable(sortedEvents)

//      val migratedEventsCache = new ListBuffer[SyntheticEvent]
      val removedEventsCache = new ListBuffer[SyntheticEvent]

      // Migrate events starting with earliest migration event
      migrationEvents.foreach(migrationEvent => {
        val sourceSRKey = getSRKey(migrationEvent.subscriber, migrationEvent.subscriberRelation)

        // get all events received on or before the migration events.
        // There should not be any events after the migration events.
        // But if there is indeed any events after the migration event make them invisible
        val eventsToBeMigrated = eventsBySR(sourceSRKey).filter(x => x.asOfPeriod <= migrationEvent.asOfPeriod)

        val (destinationSubscriber, destinationSubscriberRelation) = migrationProcessor.getDestination(migrationEvent.curatedRecordJson)
        val destinationSRKey = getSRKey(destinationSubscriber, destinationSubscriberRelation)

        // The destination S + SR does not exist and not are converting all events back to the previous renumber accounts
        if (!doDestinationSubscriberRelationExistOrSomeEventsNotMigratedYet(migrationEvent, destinationSRKey, eventsBySR)) {
          val history = MigrationHistory(migrationEvent.subscriber, migrationEvent.subscriberRelation, migrationEvent.asOfPeriod)
          // list  buffer for source events to be marked as invisible and hasmigrated true
          val hasMigratedList = ListBuffer[SyntheticEvent]()

          // Creates a shallow copy of map, so that updating the migration curated events hashmap will not change the original curatedEvent
          eventsToBeMigrated.foreach(event => {
            hasMigratedList.+=:(event.copy(curatedRecordJson = new java.util.HashMap[String, Any](event.curatedRecordJson)))
          })

          val migratedEvents = eventsToBeMigrated.map(eventToBeMigrated => {
            val migratedJson = migrationProcessor.migrateEvent(migrationEvent.curatedRecordJson, eventToBeMigrated.curatedRecordJson)
            SyntheticEvent(eventToBeMigrated.dataSource,
              destinationSubscriber,
              destinationSubscriberRelation,
              eventToBeMigrated.frequency,
              eventToBeMigrated.asOfPeriod,
              eventToBeMigrated.parentEventIds,
              migratedJson,
              eventToBeMigrated.migrationHistory :+ history)
          })

          // update EUC fields Map
          updateEUCAndLAPFields(euc, lap, migratedEvents.toList, None)

          if (eventsBySR.getOrElse(destinationSRKey, null) != null)
          // Prepend to destination
            eventsBySR.put(destinationSRKey, migratedEvents ++= eventsBySR(destinationSRKey))
          else
            eventsBySR.put(destinationSRKey, migratedEvents)



          // making all the source events invisible and marking them as migrated
          // Incase a new event comes with old subscriber and subscriber relation, using "hasMigrated" field it can be filtered out
          CustomRuleChecker.makeEventsInvisible(hasMigratedList.toList)
          markEventAsMigrated(hasMigratedList.toList, destinationSubscriber, destinationSubscriberRelation)
//          migratedEventsCache.appendAll(hasMigratedList)
          eventsBySR.put(sourceSRKey, hasMigratedList)
        }
        // destination S + SR exists
        else {
          val destinationEvents = eventsBySR(destinationSRKey)

          // Migrate the source events to new Subscriber and Relation
          val sourceEventsToBeMigrated = getToBeMigratedSourceEvents(eventsToBeMigrated, destinationEvents.head)
          val fieldsToTransformInSource = migrationProcessor.fieldsToTransformInSource
          val fieldValuesInDestination = if (fieldsToTransformInSource != null || fieldsToTransformInSource.nonEmpty)
            getFieldValuesFromDestination(destinationEvents.toList, fieldsToTransformInSource, migrationEvent)
          else
            Map.empty[String, String]
          val history = MigrationHistory(migrationEvent.subscriber, migrationEvent.subscriberRelation, migrationEvent.asOfPeriod)
          val migratedEvents = sourceEventsToBeMigrated.map(eventToBeMigrated => {
            val migratedJson = migrationProcessor.migrateSourceEvent(eventToBeMigrated.curatedRecordJson, migrationEvent.curatedRecordJson, fieldValuesInDestination)
            SyntheticEvent(eventToBeMigrated.dataSource,
              destinationSubscriber,
              destinationSubscriberRelation,
              eventToBeMigrated.frequency,
              eventToBeMigrated.asOfPeriod,
              eventToBeMigrated.parentEventIds,
              migratedJson,
              eventToBeMigrated.migrationHistory :+ history)

          })

          // Update destination events
          val fieldsToTransformInDestination = migrationProcessor.fieldsToTransformInDestination
          val destinationEventAfterMigration = if (fieldsToTransformInDestination != null && fieldsToTransformInDestination.nonEmpty) {
            val fieldValuesInSource = getFieldValuesFromSource(fieldsToTransformInDestination, migrationEvent)
            val destinationEventsAfterMigration = destinationEvents.map(toBeUpdatedEvent => {
              val migratedJson = migrationProcessor.updateDestinationEvent(toBeUpdatedEvent.curatedRecordJson,
                migrationEvent.curatedRecordJson, fieldValuesInSource)
              SyntheticEvent(toBeUpdatedEvent.dataSource, toBeUpdatedEvent.subscriber, toBeUpdatedEvent.subscriberRelation, toBeUpdatedEvent.frequency, toBeUpdatedEvent.asOfPeriod, toBeUpdatedEvent.parentEventIds, migratedJson, toBeUpdatedEvent.migrationHistory)
            })
            destinationEventsAfterMigration
          }
          else
            destinationEvents
          //Get fieldsToTransformINDest and update EUC values if any here
          updateEUCAndLAPFields(euc, lap, destinationEventAfterMigration.toList, Some(fieldsToTransformInDestination))

          // Prepend the migrated events to destination
          val migratedWithDestinationEvents = migratedEvents ++= destinationEventAfterMigration
          eventsBySR.put(destinationSRKey, migratedWithDestinationEvents)
//          migratedEventsCache.appendAll(migratedWithDestinationEvents)
          // remove migrated events from the hash
          removedEventsCache.appendAll(eventsBySR(sourceSRKey).take(sourceEventsToBeMigrated.length))
          eventsBySR(sourceSRKey).remove(0, sourceEventsToBeMigrated.length)
          // Hide any overlapping source events
          // making all the source events invisible and marking them as migrated
          // In case a new event comes with old subscriber and subscriber relation, using "hasMigrated" field it can be filtered out
          val sourceEventsToBeHidden = getToBeHiddenSourceEvents(eventsToBeMigrated.toList, destinationEventAfterMigration.head)
          CustomRuleChecker.makeEventsInvisible(sourceEventsToBeHidden)
          markEventAsMigrated(sourceEventsToBeHidden, destinationSubscriber, destinationSubscriberRelation)
        }
      })
      val eventList = eventsBySR.flatMap(x => x._2).toList
      // Fitering events that was migrated but kept original data with invisible field "true"
     val updatedEvents = eventList
       .filter(each => each.migrationHistory.size != 0 &&
       !each.curatedRecordJson.containsKey("isVisible") &&
         each.curatedRecordJson.get("isVisible") != false
       )
//      (eventsBySR.flatMap(x => x._2).toList, migratedEventsCache.toList, removedEventsCache.toList)
      (eventList, updatedEvents, removedEventsCache.toList)
    } else {
      (events, List.empty, List.empty)
    }
  }

  private def updateEUCAndLAPFields(euc: EUC, lap: LAP, migratedEvents: List[SyntheticEvent],
                                    fieldsToTransformInDestination: Option[Array[String]]): Unit = {
    val sortedSyntheticEvents = migratedEvents.sortBy(eachEvent => eachEvent.asOfPeriod)
    sortedSyntheticEvents.foreach(eachEvent => {
      euc.updateEUCAfterMigration(fieldsToTransformInDestination, eachEvent)
      lap.updateLAPAfterMigration(fieldsToTransformInDestination, eachEvent)
    })
  }

  private def markEventAsMigrated(migratedEvents: List[SyntheticEvent], destinationSubscriber: String, destinationSubscriberRelation: String) = {
    migratedEvents.map(event => {
      event.curatedRecordJson.put("hasMigrated", true)
      event.curatedRecordJson.put("destinationSubscriber", destinationSubscriber)
      event.curatedRecordJson.put("destinationSubscriberRelation", destinationSubscriberRelation)
    })
  }

  private def doDestinationSubscriberRelationExistOrSomeEventsNotMigratedYet(migrationEvent: SyntheticEvent,
                                                     destinationSRKey: Int,
                                                     eventsBySR: mutable.HashMap[Int, ListBuffer[SyntheticEvent]]): Boolean = {
    val eventsWithDestinationSR = eventsBySR.getOrElse(destinationSRKey, null)
    if (eventsWithDestinationSR == null || eventsWithDestinationSR.head.asOfPeriod > migrationEvent.asOfPeriod ||
      eventsWithDestinationSR.filter(e => !e.curatedRecordJson.containsKey("hasMigrated")).length == 0)
        false
    else
      true
  }

  private def getToBeMigratedSourceEvents(sourceEvents: ListBuffer[SyntheticEvent],
                                          destinationEvent: SyntheticEvent): ListBuffer[SyntheticEvent] = {
    sourceEvents.filter(x => x.asOfPeriod < destinationEvent.asOfPeriod)
  }

  private def getToBeHiddenSourceEvents(sourceEvents: List[SyntheticEvent],
                                        destinationEvent: SyntheticEvent): List[SyntheticEvent] = {
    sourceEvents.filter(x => x.asOfPeriod >= destinationEvent.asOfPeriod)
  }

  private def getFieldValuesFromDestination(destinationEvents: List[SyntheticEvent],
                                            fieldNames: Array[String],
                                            migrationEvent: SyntheticEvent): Map[String, String] = {

    // get the most destination event that is as old as migrationEvent
    val e = destinationEvents.filter(x => x.asOfPeriod <= migrationEvent.asOfPeriod).last

    // pick the EUC and LAP field values from the most recent event.
    val fieldValues = fieldNames.map(x => {
      x -> {
        if (e.curatedRecordJson.containsKey(x))
          e.curatedRecordJson.get(x).toString
        else
          null
      }
    }).toMap

    fieldValues
  }

  private def getFieldValuesFromSource(fieldNames: Array[String],
                                       migrationEvent: SyntheticEvent): Map[String, String] = {

    // pick the EUC and LAP field values from the most recent event.
    val fieldValues = fieldNames.map(x => {
      x -> {
        if (migrationEvent.curatedRecordJson.containsKey(x))
          migrationEvent.curatedRecordJson.get(x).toString
        else
          null
      }
    }).toMap

    fieldValues
  }


  private def buildEventHashtable(events: List[SyntheticEvent]): mutable.HashMap[Int, ListBuffer[SyntheticEvent]] = {
    val eventsBySR = mutable.HashMap[Int, ListBuffer[SyntheticEvent]]()

    events.foreach(x => {
      val k = getSRKey(x.subscriber, x.subscriberRelation)
      val group = eventsBySR.getOrElse(k, {
        val newGroup = ListBuffer[SyntheticEvent]()
        eventsBySR.put(k, newGroup)
        newGroup
      })
      group += x
    })

    eventsBySR
  }

  private def getSRKey(sub: String, rel: String): Int = {
    (sub + ":" + rel).hashCode
  }

  def mapToJsonObject(mapObject: java.util.HashMap[String, Any]): JsonObject = {
    JsonUtils.fromJson(JsonUtils.toJson[java.util.HashMap[String, Any]](mapObject), classOf[JsonObject])
  }

  def jsonObjectToMap(jsonObject: JsonObject): java.util.HashMap[String, Any] = {
    JsonUtils.fromJson(JsonUtils.toJson(jsonObject), classOf[java.util.HashMap[String, Any]])
  }

  def syntheticEventsDiff(events: ListBuffer[SyntheticEvent], against: List[SyntheticEvent]): ListBuffer[SyntheticEvent] = events diff against

}
