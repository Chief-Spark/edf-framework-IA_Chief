package com.experian.edf.oxygen.validation.extension

import com.experian.edf.oxygen.domain.internal.{DomainData, FileMetadata}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.processors.ProcessorContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.utils.JsonUtils
import com.experian.edf.oxygen.validation.context.ValidationContext
import com.experian.edf.oxygen.validation.domain._
import com.experian.edf.oxygen.validation.validator._
import com.experian.edf.oxygen.validator.internal.ObjectError
import com.google.gson.{JsonObject, JsonParser}

import scala.collection.mutable.ArrayBuffer

/**
 * Created by c15661a on 8/7/17.
 */
class GenericJsonValidationProcessor(context: ProcessorContext) extends ValidationProcessor(context){

  override def createFileMetadata: FileMetadata = new FileMetadata(fileName = context.fileName, headers = context.headers) {
    /**
     * Source of the data/file
     */
    override val dataSource: String = "DataSource"
    /**
     * Must be in epoch time
     */
    override val receivedDate: Long = System.currentTimeMillis()
    /**
     * Must be in epoch time
     */
    override val fileAsOfDate: Long = System.currentTimeMillis()
    /**
     * Deprecating subscriber soon, subscriber can be populated through DomainData in "createValidatedEvent"
     */
    override val subscriber: String = "Subscriber"
  }

  /**
   * Implementation specific method to return true if a row is a header row.
   *
   * @param row String: row to read
   * @return Boolean: Is it a header row?
   */
  override def isHeaderRow(row: String): Boolean = false

  class HeaderValidation(message : String) extends ValidationData{

    override def data: String = message
  }
  //This will only be called for HT
  override def validateHeader(validationContext: ValidationContext): ValidationResult = {
    var headerIsValid = true
    context.headers.foreach( header => {
      if (!header.startsWith("HHHHH"))
        headerIsValid = false
    })
    if (headerIsValid)
      return new ValidationSuccess(
        result = new HeaderValidation("Header is valid"),
        errors = Array.empty)
    else
      return new ValidationError(result = new HeaderValidation("Header is not valid"),
        errors = Array(ObjectError(errorCodeName = "HeaderErrorName", errorCodeValue = "HeaderErrorValue", errorLevel = "HeaderErrorLevel", messageKey = "HeaderMessageKey")))
  }

  /**
   * Implementation specific method to returns true if a row is a footer row. Default is false
   *
   * @param row String: row to read
   * @return Boolean: Is it a footer row?
   */
  override def isFooterRow(row: String): Boolean = false
  /*
  * Implement specific method to return the Validated Event, it's Domain Data, Quality Scores, Error Objects and metadata
  *
  * @param rawRecord: Single record coming from the original file
  * @param validationContext: Additional information from the validation run (applicationRunMode,historicAttributes, hfMetadata)
  * @return Array[ValidatedEventValueObject]: Parsed record(s) + validation information
  */
  override def createValidatedEvents(rawRecord: String, validationContext: ValidationContext): Array[ValidatedEventValueObject] = {
    val json = new JsonParser().parse(rawRecord).getAsJsonObject
    val event = new GenericJsonValidatedEvent(json.toString)
    //This brings the original record as Map[String, Object]
    val additionalMetadata = if (validationContext.hfMetadata.nonEmpty) validationContext.hfMetadata.get else null
    val qualityScores = new ArrayBuffer[QualityScore]
    val errors = new ArrayBuffer[ObjectError]

    //Random QS
    qualityScores += QualityScore(name = "randomQS", score = (math.random % 50 + 1).toDouble)
    //Static Error for all records
    errors += ObjectError(errorCodeName = "StaticErrorName", errorCodeValue = "StaticErrorValue", errorLevel = "StaticErrorLevel", messageKey = "StaticMessageKey")
    Array(new ValidatedEventValueObject(
      event = event,
      domainData = Option(DomainData(asOfDate = Option(System.currentTimeMillis()),
        subscriberRelation = json.get("id").toString,
        subscriber = Option("Subscriber"),
        datasource = Option("DataSource"))),
      qualityScores = qualityScores.toArray,
      errors = errors.toArray,
      metadata = Option(createFileMetadata)))
  }
  /*
  * Creating keys for duplicate checks, this will cause records to group with same keys
  */
  override def keysForDuplicate(rawRecord: ValidatedEvent): Array[Any] = {
    val rawRecordJsonObj = JsonUtils.fromJson(rawRecord.write, classOf[JsonObject])
    val id = rawRecordJsonObj.get("id").getAsString
    Array(id)
  }
  /*
  * Implement how to handle Duplicate records
  * This sample is just selecting the one with the latest as of date
  */
  override def listDuplicateEvents(events: List[TaggedValidatedEvent]): List[TaggedValidatedEvent] = {
    List(events.maxBy(_.asOfDate))
  }

  // V3 contextual validation
  override def contextPredicates: Map[String, Array[String]] = {
    Map( "Currency" -> Array("currency1", "currency2", "currency3", "currency4", "currency5"),
      "DateOfBirthValidation" -> Array("dateOfBirth"),
      "NationalIdValidation" -> Array("id"),
      "AccountNumberValidation" -> Array("accountNumber"))
  }

  override def contextuallyValidate(candidateEvents: Array[TaggedValidatedEvent],
                                    contextName: String,
                                    contextualEvents: Array[TaggedCuratedEventWithMap]): Array[(TaggedValidatedEvent, Array[QualityScore], Array[ObjectError])] = {
    if (contextName == "Currency") {
      candidateEvents.map(x => {
        (x, Array(QualityScore("currency", 10.0)), Array(ObjectError("currency", "currency", "1", "", "empty")))
      })
    }
    else if (contextName == "DateOfBirthValidation") {
      candidateEvents.map(x => {
        (x, Array(QualityScore("dateOfBirth", 10.0)), Array(ObjectError("dateOfBirth", "dateOfBirth", "1", "", "empty")))
      })
    }
    else if (contextName == "NationalIdValidation"){
      candidateEvents.map(x => {
        (x, Array(QualityScore("nationalId", 10.0)), Array(ObjectError("nationalId", "nationalId", "1", "", "empty")))
      })
    }
    else {
      candidateEvents.map(x => (x, Array.empty[QualityScore], Array.empty[ObjectError]))
    }
  }
}