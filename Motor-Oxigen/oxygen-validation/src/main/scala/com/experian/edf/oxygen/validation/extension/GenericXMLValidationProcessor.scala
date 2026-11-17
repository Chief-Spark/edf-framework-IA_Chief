package com.experian.edf.oxygen.validation.extension

import com.experian.edf.oxygen.domain.internal.{DomainData, FileMetadata, Payload}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.processors.ProcessorContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.utils.JsonUtils
import com.experian.edf.oxygen.validation.context.ValidationContext
import com.experian.edf.oxygen.validation.domain._
import com.experian.edf.oxygen.validation.extension.metadata.CreditFileMetadata
import com.experian.edf.oxygen.validation.validator._
import com.experian.edf.oxygen.validator.internal.ObjectError
import com.google.gson.{JsonObject, JsonParser}
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer



class GenericXMLValidationProcessor(context: ProcessorContext) extends ValidationProcessor(context){
  val NationalIDPredicate = "NationaIDPredicate"
  val PastDueValidationPredicate = "PastDueValidationPredicate"
  val NationalIDDataSource = "BDIIREGI"
  val NationalIDSubscriber = "BDIIREGI"
  @transient lazy val logger = getLogger(this.getClass)
  lazy val asOfDate: Long = fileMetadata.asInstanceOf[CreditFileMetadata].asOfDate
  lazy val subscriber = fileMetadata.asInstanceOf[CreditFileMetadata].subscriber
  override def createFileMetadata: FileMetadata = new FileMetadata(fileName = context.fileName, headers = context.headers) {
    /**
     * Source of the data/file
     */
    override val dataSource: String = "230001"
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

  override def enrichEventWithPayload(event: ValidatedEvent): Payload = {
    val payloadMap = Map[String, String]("thouInd" -> "M")
    new Payload(payloadMap)
  }
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
        subscriberRelation = json.get("nationalId").toString,
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
    val id = rawRecordJsonObj.get("nationalId").getAsString
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
  /* override def contextPredicates: Map[String, Array[String]] = {
     Map( "Currency" -> Array("currency1", "currency2", "currency3", "currency4", "currency5"),
          "DateOfBirthValidation" -> Array("dateOfBirth"),
          "NationalIdValidation" -> Array("id"),
          "AccountNumberValidation" -> Array("accountNumber"))
   }*/
  override def contextPredicates(pendingTaggedAcceptedEvent: TaggedValidatedEvent): Map[String, (String, String, String, Int)] = {
    val predicates = mutable.Map[String, (String, String, String, Int)]()

    val pendingAcceptedEvent = new CreditAcceptedEvent(pendingTaggedAcceptedEvent.validatedRecordJson)

    // Predicate to get contextual events for National Id validation
    predicates += (NationalIDPredicate -> (NationalIDDataSource,
      NationalIDSubscriber, pendingAcceptedEvent.nationalId, 1))

    // Predicate to get contextual events for
    predicates += (PastDueValidationPredicate -> (pendingTaggedAcceptedEvent.lineage.dataSource,
      pendingTaggedAcceptedEvent.lineage.subscriber, pendingAcceptedEvent.accountNumber, 3))

    predicates.toMap
  }

  /*override def contextuallyValidate(candidateEvents: Array[TaggedValidatedEvent],
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
  }*/
  override def contextuallyValidate(candidateEvents: List[TaggedValidatedEvent],
                                    contextualEvents: Map[String, List[TaggedCuratedEventWithMap]]): List[(ValidatedEvent, Array[QualityScore], Array[ObjectError])] = {

    val contextuallyValidatedEvents = candidateEvents.map(x => {
      val candidateEvent = x

      val creditValidatedEvent = new CreditValidatedEvent(candidateEvent.validatedRecordJson)
      val qualityScores = new ArrayBuffer[QualityScore]()
      val errors = new ArrayBuffer[ObjectError]()

      contextualEvents.map { case (k, v) =>
        val predicateName = k
        val events = v

        if (predicateName == NationalIDPredicate) {
          qualityScores += validateNationalId(candidateEvent, events)

        } else if (predicateName == PastDueValidationPredicate) {
          qualityScores += validatePastDueDate(candidateEvent, events)
        }
      }

      // val creditValidatedEvent = if(candidateEvent.rawRecord.contains("ROSA")) {
      // new CreditValidatedEvent(candidateEvent.validatedRecord)

      (creditValidatedEvent, qualityScores.toArray, errors.toArray)
    })

    contextuallyValidatedEvents
  }
  private def validateNationalId(candidateEvent: TaggedValidatedEvent, events: List[TaggedCuratedEventWithMap]): QualityScore = {
    QualityScore("NationalIdScore", 100)
  }
  def scoreNationalId(nationalId: String): (String, Int) = {
    if (nationalId.length < 10) {
      ("q2", 0)
    } else
      ("q2", 99)
  }
  private def validatePastDueDate(candidateEvent: TaggedValidatedEvent, events: List[TaggedCuratedEventWithMap]): QualityScore = {
    QualityScore("PastDueDateScore", 100)
  }
}