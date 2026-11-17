package com.experian.edf.oxygen.consistencycheck.extension.migration

import com.experian.edf.oxygen.consistencycheck.extension.util.Util
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationProcessor
import com.experian.edf.oxygen.domain.processors.MigrationProcessorContext
import org.slf4j.LoggerFactory.getLogger

import java.util

class RelationshipMigrationProcessorImpl(context: MigrationProcessorContext ) extends RelationshipMigrationProcessor(context: MigrationProcessorContext ) {

  @transient lazy val logger = getLogger(this.getClass)

  /* override def requiresMigration(eventJson: JsonObject): Boolean = {
    val oxygenLookupServ = context.lookup.value
    val lookupRetValue = Util.getLookup("consistencyfiltertable1", Map("errorcode" -> "errorCode1"),"errorvalue", oxygenLookupServ )
    //logger.debug(lookupRetValue.head)
    if (eventJson.has("isMigration") && lookupRetValue.head == "02")
      eventJson.get("isMigration").getAsBoolean
    else
      false
  }*/

  override def requiresMigration(eventMap: util.Map[String, Any]): Boolean = {
    if (eventMap.containsKey("isMigration"))
      eventMap.get("isMigration").asInstanceOf[Boolean]
    else
      false
  }

  /* override def getDestination(migrationEventJson: JsonObject): (String, String) = {
    (migrationEventJson.get("destinationSubscriber").getAsString,
      migrationEventJson.get("destinationSubscriberRelation").getAsString)
  }
*/

  override def getDestination(eventMap: util.Map[String, Any]): (String, String) = {
    (eventMap.get("destinationSubscriber").toString,
      eventMap.get("destinationSubscriberRelation").toString)
  }

  /**
   *
   * @param migrationEventJson
   * @param toBeMigratedEventJson Mutable
   */
  /*  override def migrateEvent(migrationEventJson: JsonObject, toBeMigratedEventJson: JsonObject): JsonObject = {
    val sourceAccountType = migrationEventJson.get("accountType").getAsString
    val destinationAccountType = migrationEventJson.get("destinationAccountType").getAsString

    val oxygenLookupServ = context.lookup.value
    val lookupRetValue = Util.getLookup("consistencyfiltertable1", Map("errorcode" -> "errorCode1"),"errorvalue", oxygenLookupServ )
    //logger.debug(lookupRetValue.head)
    if(sourceAccountType == "01" && destinationAccountType != sourceAccountType && lookupRetValue.head == "02")
      toBeMigratedEventJson.addProperty("accountStatus", "99")

    toBeMigratedEventJson
  }*/

  override def migrateEvent(migrationEvent: util.Map[String, Any], toBeMigratedEvent: util.Map[String, Any]): util.Map[String, Any] = {
    val otherEvent = toBeMigratedEvent
    val sourceAccountType = migrationEvent.get("accountType").asInstanceOf[String]
    println(sourceAccountType)
    val destinationAccountType = migrationEvent.get("destinationAccountType").asInstanceOf[String]
    println(destinationAccountType)
    val oxygenLookupServ = context.lookup
    val lookupRetValue = Util.getLookup("consistencyfiltertable1", Map("errorcode" -> "errorCode1"), "errorvalue", oxygenLookupServ)
    println("^^^^^^^", lookupRetValue.head)
    println(toBeMigratedEvent.get("accountStatus"))
    if (sourceAccountType == "01" && destinationAccountType != sourceAccountType && lookupRetValue.head == "02")
      otherEvent.put("accountStatus", "99")
    otherEvent
  }


  /**
   * The field names that should be updated in the source
   *
   * @return LAP and EUC field names that need to be transformed.  The fields Must be either EUC or LAP.
   */
  override def fieldsToTransformInSource: Array[String] = Array("accountOpeningDate", "accountExpirationDate")

  /* override def migrateSourceEvent(toBeMigratedEventJson: JsonObject,
                                   migrationEventJson: JsonObject,
                                   fieldsFromDestination: Map[String, String]): JsonObject = {
     fieldsFromDestination.foreach(x => {
       val fieldName = x._1
       val fieldValue = x._2
       if (fieldName == "accountOpeningDate") {
         val destinationAccountOpeningDate = fieldValue
         val sourceAccountOpeningDate = toBeMigratedEventJson.get("accountOpeningDate").getAsString
         if (destinationAccountOpeningDate < sourceAccountOpeningDate)
           toBeMigratedEventJson.addProperty("accountOpeningDate", destinationAccountOpeningDate)
       }
       if (fieldName == "accountExpirationDate") {
         val destinationAccountExpirationDate = fieldValue
         val sourceAccountAccountExpirationDate  = toBeMigratedEventJson.get("accountExpirationDate").getAsString
         if (destinationAccountExpirationDate > sourceAccountAccountExpirationDate)
           toBeMigratedEventJson.addProperty("accountExpirationDate", destinationAccountExpirationDate)
       }
     })

     toBeMigratedEventJson
   }*/


  override def migrateSourceEvent(toBeMigratedEvent: util.Map[String, Any], migrationEvent: util.Map[String, Any], fieldsFromDestination: Map[String, String]): util.Map[String, Any] = {
    fieldsFromDestination.foreach(x => {
      val fieldName = x._1
      val fieldValue = x._2
      if (fieldName == "accountOpeningDate") {
        val destinationAccountOpeningDate = fieldValue
        val sourceAccountOpeningDate = toBeMigratedEvent.get("accountOpeningDate").toString
        if (destinationAccountOpeningDate < sourceAccountOpeningDate)
          toBeMigratedEvent.put("accountOpeningDate", destinationAccountOpeningDate)
      }
      if (fieldName == "accountExpirationDate") {
        val destinationAccountExpirationDate = fieldValue
        val sourceAccountAccountExpirationDate = toBeMigratedEvent.get("accountExpirationDate").toString
        if (destinationAccountExpirationDate > sourceAccountAccountExpirationDate)
          toBeMigratedEvent.put("accountExpirationDate", destinationAccountExpirationDate)
      }
    })

    toBeMigratedEvent
  }


  override def fieldsToTransformInDestination: Array[String] = Array("accountOpeningDate", "accountExpirationDate")

  /*  override def updateDestinationEvent(toBeUpdatedEventJson: JsonObject,
                                        migrationEventJson: JsonObject,
                                        fieldsFromSource: Map[String, String]): JsonObject = {
      fieldsFromSource.foreach(x => {
        val fieldName = x._1
        val fieldValue = x._2
        if (fieldName == "accountOpeningDate") {
          val sourceAccountOpeningDate = fieldValue
          val destinationAccountOpeningDate = if (toBeUpdatedEventJson.has(fieldName))toBeUpdatedEventJson.get("accountOpeningDate").getAsString else null
          if (destinationAccountOpeningDate > sourceAccountOpeningDate)
            toBeUpdatedEventJson.addProperty("accountOpeningDate", sourceAccountOpeningDate)
        }
        if (fieldName == "accountExpirationDate") {
          val sourceAccountAccountExpirationDate = fieldValue
          val destinationAccountExpirationDate  = toBeUpdatedEventJson.get("accountExpirationDate").getAsString
          if (destinationAccountExpirationDate < sourceAccountAccountExpirationDate)
            toBeUpdatedEventJson.addProperty("accountExpirationDate", sourceAccountAccountExpirationDate)
        }
      })

      toBeUpdatedEventJson
    }*/

  override def updateDestinationEvent(toBeUpdatedEventJson: util.Map[String, Any], migrationEventJson: util.Map[String, Any], fieldsFromSource: Map[String, String]): util.Map[String, Any] = {
    fieldsFromSource.foreach(x => {
      val fieldName = x._1
      val fieldValue = x._2
      if (fieldName == "accountOpeningDate") {
        val sourceAccountOpeningDate = fieldValue
        val destinationAccountOpeningDate = if (toBeUpdatedEventJson.containsKey(fieldName)) toBeUpdatedEventJson.get("accountOpeningDate").toString else null
        if (destinationAccountOpeningDate > sourceAccountOpeningDate)
          toBeUpdatedEventJson.put("accountOpeningDate", sourceAccountOpeningDate)
      }
      if (fieldName == "accountExpirationDate") {
        val sourceAccountAccountExpirationDate = fieldValue
        val destinationAccountExpirationDate = toBeUpdatedEventJson.get("accountExpirationDate").toString
        if (destinationAccountExpirationDate < sourceAccountAccountExpirationDate)
          toBeUpdatedEventJson.put("accountExpirationDate", sourceAccountAccountExpirationDate)
      }
    })

    toBeUpdatedEventJson
  }
}