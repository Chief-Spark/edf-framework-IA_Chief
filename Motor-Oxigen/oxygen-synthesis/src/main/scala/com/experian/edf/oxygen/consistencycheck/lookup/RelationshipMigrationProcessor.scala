package com.experian.edf.oxygen.pinning.lookup

import com.experian.edf.oxygen.domain.processors.{MigrationProcessorContext, OxygenProcessor}
import com.experian.edf.oxygen.domain.synthesis.IRelationshipMigrationProcessor
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.utils.JsonUtils
import com.google.gson.JsonObject

abstract class RelationshipMigrationProcessor(migrationProcessorContext: MigrationProcessorContext) extends OxygenProcessor with IRelationshipMigrationProcessor {

  /*
    def this(context: MigrationProcessorContext) = {
      this(context.lookup)
    }
  */

  override def lookupService: OxygenLookup = migrationProcessorContext.lookup


  /**
   *
   * @param eventJson
   * @return true if the event is of migration type, otherwise false
   */
  @deprecated
  def requiresMigration(eventJson: JsonObject): Boolean = ???

  def requiresMigration(eventMap: java.util.Map[String,Any]): Boolean = {
    requiresMigration(mapToJsonObject(eventMap))
  }


  /**
   *
   * @param migrationEventJson
   * @return given the migration event, returns the destination Subscriber, Subscriber Relation
   */
  @deprecated
  def getDestination(migrationEventJson: JsonObject): (String, String) = ???

  def getDestination(eventMap: java.util.Map[String,Any]): (String, String) = {
    getDestination(mapToJsonObject(eventMap))
  }

  /**
   *
   * @param migrationEventJson
   * @param toBeMigratedEventJson Mutable
   */
  @deprecated
  def migrateEvent(migrationEventJson: JsonObject, toBeMigratedEventJson: JsonObject): JsonObject = ???

  def migrateEvent(migrationEvent: java.util.Map[String,Any], toBeMigratedEvent: java.util.Map[String,Any]): java.util.Map[String,Any] = {
    val migrateEventJson = migrateEvent(mapToJsonObject(migrationEvent),mapToJsonObject(toBeMigratedEvent))
    jsonObjectToMap(migrateEventJson)
  }


  // Merge interfaces
  /**
   * The field names that should be updated in the source
   * @return  LAP and EUC field names that need to be transformed.  The fields Must be either EUC or LAP.
   */
  def fieldsToTransformInSource: Array[String]

  /**
   * Performs the migration of source events that existed prior to destination events.
   * Period          4       5       6       7       8
   * Source:         s       s       m
   * Destination:            d       d       d       d
   *
   * Results:        s'      d'      d'      d'      d'
   *
   *
   * @param toBeMigratedEventJson
   * @param migrationEventJson
   * @param fieldsFromDestination
   * @return
   */
  @deprecated
  def migrateSourceEvent(toBeMigratedEventJson: JsonObject, migrationEventJson: JsonObject, fieldsFromDestination:Map[String, String]): JsonObject = ???

  def migrateSourceEvent(toBeMigratedEvent: java.util.Map[String,Any], migrationEvent: java.util.Map[String,Any], fieldsFromDestination:Map[String, String]): java.util.Map[String,Any] = {
    val migrateSourceEventAsJson = migrateSourceEvent(mapToJsonObject(toBeMigratedEvent),mapToJsonObject(migrationEvent),fieldsFromDestination)
    jsonObjectToMap(migrateSourceEventAsJson)
  }

  /**
   * The field names that should be updated.
   * @return
   */
  def fieldsToTransformInDestination: Array[String]

  /**
   * Update the destination events given the migration event and fields values from the source.
   * @param toBeUpdatedEventJson
   * @param migrationEventJson
   * @param fieldsFromSource
   * @return
   */
  @deprecated
  def updateDestinationEvent(toBeUpdatedEventJson: JsonObject, migrationEventJson: JsonObject, fieldsFromSource: Map[String, String]): JsonObject = ???

  def updateDestinationEvent(toBeUpdatedEvent: java.util.Map[String, Any], migrationEvent: java.util.Map[String, Any], fieldsFromSource: Map[String, String]): java.util.Map[String, Any] = {
    val updatedJsonObject = updateDestinationEvent(mapToJsonObject(toBeUpdatedEvent), mapToJsonObject(migrationEvent),fieldsFromSource)
    jsonObjectToMap(updatedJsonObject)
  }


  def mapToJsonObject(mapObject: java.util.Map[String,Any]): JsonObject = {
    JsonUtils.fromJson(JsonUtils.toJson[java.util.Map[String,Any]](mapObject),classOf[JsonObject])
  }

  def jsonObjectToMap(jsonObject: JsonObject): java.util.Map[String,Any] = {
    JsonUtils.fromJson(JsonUtils.toJson(jsonObject),classOf[java.util.Map[String,Any]])
  }

}
