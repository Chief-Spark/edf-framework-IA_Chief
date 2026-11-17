package com.experian.edf.oxygen.validation.extension

import com.experian.edf.oxygen.domain.internal.{DomainData, MetricData, Payload, TaggedCuratedEvent}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.processors.ProcessorContext
import com.experian.edf.oxygen.domain.{HistoricAttributes, TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.exception.{MalformedFileException, MalformedHeaderException}
import com.experian.edf.oxygen.utils.JsonUtils
import com.experian.edf.oxygen.validation.context.ValidationContext
import com.experian.edf.oxygen.validation.domain.ApplicationRunMode.{BATCH, STREAM}
import com.experian.edf.oxygen.validation.domain._
import com.experian.edf.oxygen.validation.extension.event.CreditCuratedEvent
import com.experian.edf.oxygen.validation.extension.metadata.CreditFileMetadata
import com.experian.edf.oxygen.validation.validator._
import com.experian.edf.oxygen.validator.internal.ObjectError
import com.google.gson.{Gson, JsonArray, JsonObject}
import org.slf4j.LoggerFactory.getLogger

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Created by c15661a on 8/7/17.
 */
class CreditEventValidationProcessor(procCtx: ProcessorContext) extends ValidationProcessor(procCtx) {

  // The two mandatory methods which need to be defined by the implementation
  val headers = procCtx.headers
  val footers = procCtx.footers
  val fileName = procCtx.fileName

  val NationalIDPredicate = "NationaIDPredicate"
  val PastDueValidationPredicate = "PastDueValidationPredicate"
  val NationalIDDataSource = "BDIIREGI"
  val NationalIDSubscriber = "BDIIREGI"

  @transient lazy val logger = getLogger(this.getClass)

  lazy val asOfDate: Long = fileMetadata.asInstanceOf[CreditFileMetadata].asOfDate
  lazy val subscriber = fileMetadata.asInstanceOf[CreditFileMetadata].subscriber

  override def validateHistoricAttributes(metricData: MetricData, historic: JsonArray): ValidationResult = {

    try {
      val dataSource = metricData.fileMetadata.dataSource
      val subScriber = metricData.fileMetadata.subscriber

      val currentQualityScore = metricData.metrics.get("AverageOfQualityscore").toDouble
      val historicValue = if (historic.size() !=0)
        historic.get(0).getAsJsonObject.get("validation").getAsJsonObject.get("datasetmetrics").getAsJsonObject.get("AverageOfQualityscore").getAsDouble
      else 0.0
      logger.error("dataSource is " + dataSource + " subScriber " + subScriber + " " + currentQualityScore + " " + historicValue)

      if (currentQualityScore > historicValue) {
        new ValidationSuccess(new ValidationData, Array(new ObjectError("error", "HIST0001", "1", "", "trendError")))
      }
      else {
        new ValidationSuccess(new ValidationData, Array(new ObjectError("noError", "HIST0002", "2", "", "trendError")))
      }
    } catch {
      case _: Exception => new ValidationSuccess(new ValidationData, Array(ObjectError("noError", "HIST0002", "2", "", "trendError")))
    }
  }

  override def keysForDuplicate(validatedEvent: ValidatedEvent): Array[Any] = {
    val rawRecordJsonObj = JsonUtils.fromJson(validatedEvent.write.toString(), classOf[JsonObject])
    val id = rawRecordJsonObj.get("nationalId").getAsString
    val fullName = rawRecordJsonObj.get("fullName").getAsString

    Array(id, fullName)
  }

  //override def createValidatedEvent(rawRecord: String): (ValidatedEvent, DomainData, Array[QualityScore], Array[ObjectError]) = {
  def createValidatedEventForBatch(rawRecord: String, historicAttributes: Option[JsonArray]): (ValidatedEvent, DomainData, Array[QualityScore], Array[ObjectError]) = {

    def rand(): Int = {
      (math.random * (100 - 50) + 50).toInt
    }

    def rand10(): Int = {
      (math.random * (10 - 5) + 5).toInt
    }

    val historicValue = if (historicAttributes.isDefined && historicAttributes.get.size() !=0)
    {
      logger.debug("historic attributes is defined in createValidatedEvents "+ historicAttributes.get.toString)
      try {
        historicAttributes.get.get(0).getAsJsonObject.get("validation").getAsJsonObject.get("datasetmetrics").getAsJsonObject.get("AverageOfQualityscore").getAsDouble
      }
      catch {
        case _:Exception => {
          logger.error("Error retriving historic attributes - validation.datasetmetrics.AverageOfQualityScore. Make sure validation is including in opsAnalytics config")
          0.0
        }
      }
    }
    else {
      logger.debug("historic attributes is not defined")
      0.0
    }

    logger.error("historic attributes value:" + historicValue)

    val qualityScores = new ArrayBuffer[QualityScore]()
    val errorCodes = new ArrayBuffer[ObjectError]()
    val event = new CreditValidatedEvent(rawRecord)
    if (event.idType == -1) qualityScores += QualityScore("idNumberPresent", 0) else qualityScores += QualityScore("idNumberPresent", 1)

    if (event.ageOfMora < 31) qualityScores += QualityScore("30Mora", 1)
    else if (event.ageOfMora < 61) qualityScores += QualityScore("60Mora", 1)
    else if (event.ageOfMora < 91) qualityScores += QualityScore("90Mora", 1)
    else qualityScores += QualityScore("120Mora", 1)

    if (rawRecord.contains("SAM")) {
      // if (rawRecord.length < 75) {
      qualityScores += (QualityScore("qs1", 0), QualityScore("qs2", 0), QualityScore("qs3", 0), QualityScore("qs4", 0),
        QualityScore("qualityscore1", rand), QualityScore("missingfields", 0), QualityScore("transactionid", 0), QualityScore("AC_QS1", 0), QualityScore("FQS1", 1001), QualityScore("AC_QS2", 0), QualityScore("FQS2", 1000002))
      //(new CreditValidatedEvent(rawRecord), qualityScores.toArray, errorCodes.toArray)
      (event, DomainData(Some(asOfDate), NationalIDSubscriber), qualityScores.toArray, Array(ObjectError("", "CREDVB00103", "level-01", "", ""), ObjectError("", "CREDVB00203", "level-02", "", "")))
    }
    else {
      val nationalIdScore = scoreNationalId(rawRecord.substring(1, 12).trim)
      if (nationalIdScore._2 == 0) {
        qualityScores += (QualityScore("qs3", rand), QualityScore("qs4", rand), QualityScore("qs1", rand), QualityScore("qs2", rand),
          QualityScore("AC_QS1", 1), QualityScore("FQS1", 1001), QualityScore("AC_QS2", 1), QualityScore("FQS2", 1000002))
        errorCodes += (ObjectError("", "CREDVB00301", "level-01", "", ""))
      }

      else {
        qualityScores += (QualityScore("qs1", rand), QualityScore("qs2", rand), QualityScore("qs3", rand), QualityScore("qs4", rand),
          QualityScore("qualityscore1", rand), QualityScore("missingfields", 1), QualityScore("transactionid", rand10), QualityScore("AC_QS1", 1), QualityScore("FQS1", 1001), QualityScore("AC_QS2", 1), QualityScore("FQS2", 1000002))

        errorCodes += ObjectError("", "CREDVB00501", "level-02", "", "")
        errorCodes += ObjectError("", "CREDVB00706", "level-02", "", "")
        //errorCodes += ObjectError("error2", "1002", "", "")
      }

      (event, DomainData(Some(asOfDate), NationalIDSubscriber), qualityScores.toArray, errorCodes.toArray)

    }
  }

  //override def createValidatedEventObjects(rawRecord: String, hfEventData: Option[Map[String, Object]]): Array[ValidatedEventValueObject] = {
  def createValidatedEventforHF(rawRecord: String, hfEventData: Option[Map[String, Object]]): Array[ValidatedEventValueObject] = {
    hfEventData match {
      case None => super.createValidatedEventObjects(rawRecord)
      case Some(eventData) => {
        // Create the validated event from the raw data
        val record = new CreditValidatedEvent(rawRecord)
        logger.debug("the record in createHFValidatedEvents is " + record.write)

        // Extract the metadata from the map
        val metadata = eventData("metadata").asInstanceOf[Map[String, Object]]

        // Extract subscriber and asOfDate from the metadata
        val (subscriber, fileAsOfDate) = extractFields(eventData("messageType").toString)

        val eventMetadata = new CreditFileMetadata(eventData("messageType").toString, Array(metadata("header").toString))

        // Create the domain data object to send back
        val domainData = DomainData(Some(fileAsOfDate), record.nationalId)

        // Make some quality score calculations and error calculations and populate the arrays
        val qs1 = if (record.nationalId != "1015479452") QualityScore("qs1", 90) else QualityScore("qs1", 70)
        val error = if (record.idType == 0) ObjectError("IDError", "S1001", "", "") else ObjectError("IDError", "S1002", "", "")


        val retList = List(new ValidatedEventValueObject(record, Option(domainData), Array(qs1), Array(error), Option(eventMetadata)))

        retList.toArray
      }
    }

  }

  /**
   * Going forward, we should be using this method for both high frequency and batch events
   * @param rawRecord
   * @param validationContext
   * @return
   */
  override def createValidatedEvents(rawRecord: String, validationContext: ValidationContext): Array[ValidatedEventValueObject] = {
    val lambdaEventType = validationContext.applicationRunMode
    lambdaEventType match {
      case BATCH => {
        val validatedEvent = createValidatedEventForBatch(rawRecord, validationContext.historicAttributes )
        val validatedEventObject = new ValidatedEventValueObject(validatedEvent._1, Some(validatedEvent._2), validatedEvent._3, validatedEvent._4)
        Array(validatedEventObject)
      }

      case STREAM => {
        createValidatedEventforHF(rawRecord, validationContext.hfMetadata)
      }
    }
  }
  def extractFields(fileName: String): (String, Long) = {
    val filePattern = if (fileName.endsWith("xml")) {
      "(.*[E|X]-)(\\d{6}).(\\d{8}).xml".r
    } else {
      "(.*[E|X]-)(\\d{6}).(\\d{8}).T".r
    }
    //    val filePattern = "(.*[E|X]-)(\\d{6}).(\\d{8}).T".r
    val filePattern(prefix, subscriber, cutOffDate) = fileName
    (subscriber.toString, ZonedDateTime.parse(cutOffDate + " 00:00:00 Etc/UTC", DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss VV")).toEpochSecond)
  }


  // These fields are required fields from the Processor. They tag certain key fields needed for the validation
  // framework
  @throws(classOf[MalformedFileException])
  override def createFileMetadata = new CreditFileMetadata(fileName, headers)

  lazy val creditFileMetadata = fileMetadata.asInstanceOf[CreditFileMetadata]

  //val qsExpression = lookup.getQualityScoreExpressions(fileName)

  /**
   *
   * @return
   */
  override def validateHeader(validationContext: ValidationContext): ValidationResult = {
    val historicAttributes = validationContext.historicAttributes
    val historicValue = if (historicAttributes.isDefined && historicAttributes.get.size() !=0)
    {
      logger.debug("historic attributes is defined in header validation "+ historicAttributes.get.toString)
      try {
        historicAttributes.get.get(0).getAsJsonObject.get("validation").getAsJsonObject.get("datasetmetrics").getAsJsonObject.get("AverageOfQualityscore").getAsDouble
      }
      catch {
        case _:Exception => {
          logger.error("Error retriving historic attributes - validation.datasetmetrics.AverageOfQualityScore. Make sure validation is including in opsAnalytics config")
          0.0
        }
      }
    }
    else {
      logger.debug("historic attributes is not defined")
      0.0
    }

    logger.error("historic attributes value:" + historicValue)
    if (headers.length > 0) {
      val headerRow = headers(0)
      // if (headerRow.trim.matches("^HHHHHHHHHHHHHHHHHH.*"))
      if (headerRow.trim.matches("^HHH.*"))
        new ValidationSuccess(new CreditValidationLog, Array(new ObjectError("errorcode", "CREDVH00001", "1", "", "")))
      else if (headerRow.trim.matches("^RRRRRRRRRRRRRRRRRRR.*")) {
        new ValidationWarn(new CreditValidationLog, Array(new ObjectError("", "CREDVH00006", "", "", "")))
      }
      else {
        //lookupLogger.info((new LookupUpdateLoggingEvent("account","event","22", "23", "CREATE","Missing value 22 in lookup for account table ")).toString)
        new ValidationError(new CreditValidationLog, Array(new ObjectError("", "CREDVH00005", "", "", "")))
      }
    } else {
      new ValidationError(new CreditValidationLog, Array(new ObjectError("", "CREDVH00002", "", "", "")))
    }
  }

  override def validateFooter: ValidationResult = {
    if (footers.length > 0) {
      val footerRow = footers(footers.length - 1)
      if (footerRow.trim.matches("^ZZZZZZZZZZZZZZZZZZ.*"))
        new ValidationSuccess(new CreditValidationLog, Array(new ObjectError("errorcode", "CREDVF00001", "1", "", "")))
      else
        new ValidationError(new CreditValidationLog, Array(new ObjectError("", "CREDVF00003", "", "", "")))
    }
    else {
      new ValidationError(new CreditValidationLog, Array(new ObjectError("", "CREDVF00001", "", "", "")))
    }
  }

  // override def processEmptyFile: ValidationResult = new ValidationError(new CreditValidationLog, Array(new ObjectError("EMPTY001", "\"The file is Empty\"", "", "")))

  override def validateFilename: ValidationResult = {
    if (fileName.contains("00000")) {
      new ValidationError(new CreditValidationLog, Array(new ObjectError("", "CREDFN00001", "", "", "")))
    }
    else {
      new ValidationSuccess(new CreditValidationLog, Array.empty)
    }

  }

  @throws(classOf[MalformedHeaderException])
  override def parseHeader(record: Array[String]): Array[String] = {
    Array("")
  }

  /** override def enrichEventWithPayload(event: ValidatedEventValueObject): Payload = {
    val payloadMap = mutable.Map[String, String]("messageType" -> "credit", "clientInfo" -> "cust001", "emergencyCode" -> "95905")
    val customMap = mutable.Map[String, String]("customField1" -> "fileUsed", "customField2" -> "configUsed")

    //val payloadWithCustomMap = addCustomFieldsToPayload(payloadMap, customMap)
    new Payload(payloadMap.toMap)
  }*/

  override def enrichEventWithPayload(event: ValidatedEvent): Payload = {

    val payloadMap = mutable.Map[String, String]("messageType" -> "credit", "extra3" -> "vanilaSyrup1", "extra4" -> "soyMilk")

    new Payload(payloadMap.toMap)
  }

  override def isHeaderRow(row: String): Boolean = {
    if (row.trim.matches("^HHHH.*"))
      true
    else
      false
  }

  override def isFooterRow(row: String): Boolean = {
    if (row.trim.matches("^ZZZZZ.*"))
      true
    else
      false
  }

  override def shouldFilterRow(row: String): Boolean = if (row.trim.isEmpty | row == "") true
  else
    false


  def scoreNationalId(nationalId: String): (String, Int) = {
    if (nationalId.length < 10) {
      ("q2", 0)
    } else
      ("q2", 99)
  }

  def getSubscriberCode(rec: String): String = fileMetadata.subscriber

  def getAccountNumber(rec: String): String = rec.substring(12, 30)


  def scoreSomething(validatedEvent: CreditValidatedEvent, taggedCuratedEvents: List[TaggedCuratedEvent]): QualityScore = {
    for (e <- taggedCuratedEvents) {
      val curatedRec = new CreditCuratedEvent(e.curatedRecord)
      if (validatedEvent.accountNumber == curatedRec.accountNumber)
        return QualityScore("cq", 100)
      else
        return QualityScore("cq", 10)
    }
    QualityScore("cq", 50)
  }

  override def enrichEventWith(event: ValidatedEvent): Array[String] = null

  /**
   * Sample class which extends ValidationData to send across to the
   * ValidationProcessor
   */

  class CreditValidationLog extends ValidationData {
    val message1 = "Message 1"
    val message2 = "Message2"

    override def data: String = message1 + " " + message2
  }

  override val outputEventType: Int = 5

  //
  //
  // Simplified Contextual Look up Implementation
  //
  //
  //

  /**
   * Tells the framework how to perform filtering, grouping, and applying limit on the contextual records.
   *
   * The framework applies filter on DataSource, subscriber (if not null), SubscriberRelation (if not null),
   * performs grouping on name of the predicate.
   *
   * @param pendingTaggedAcceptedEvent
   * @return a Map:
   *         with Key as name of the predicate (e.g. "NationalIDValidation", "DueDateValidation")
   *         With Values as a tuple of
   *         Data Source
   *         Subscriber
   *         SubscriberRelation
   *         Limit
   */
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

  override def subscriberRelation(pendingTaggedAcceptedEvent: TaggedValidatedEvent): String = {
    val pendingAcceptedEvent = new CreditAcceptedEvent(pendingTaggedAcceptedEvent.validatedRecordJson)
    pendingAcceptedEvent.accountNumber
  }


  /**
   * Validate CandidateEvents against set of ContextualEvents.
   *
   * @param candidateEvents  List of events that need to be validated
   * @param contextualEvents Map of predicate name and list of contextualEvents
   * @return Score and Error Codes
   *
   */
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

  private def validatePastDueDate(candidateEvent: TaggedValidatedEvent, events: List[TaggedCuratedEventWithMap]): QualityScore = {
    QualityScore("PastDueDateScore", 100)
  }

  override def createHistoricAttributesPredicates(): Option[(HistoricAttributes, Int)]
  //    = Some(HistoricAttributes(queryField1 = fileMetadata.subscriber, queryField2 = Some(fileMetadata.dataSource),queryField3 = Some("Credit"), payload = "{}",accepted = true), 10)
  = Some(HistoricAttributes(datasource = fileMetadata.dataSource, subscriber, queryField1 = fileMetadata.subscriber, queryField2 = Some(fileMetadata.dataSource), queryField3 = Some("Credit"), payload = "{}", accepted = true), 5)

  //  override def publishHistoricAttributes(metrics: Array[Map[String, String]], previousPayloads: Option[List[HistoricAttributes]]): String = new Gson().toJson(metrics.flatMap(x => x).toList)
  override def publishHistoricAttributes(metrics: Array[Map[String, String]], previousPayloads: Option[List[HistoricAttributes]]): String = new Gson().toJson(metrics.map(x => x.seq).toList)

  override def validateHistoricAttributes(currentMetrics: Array[Map[String, String]], previousPayloads: Option[List[HistoricAttributes]]): ValidationResult = super.validateHistoricAttributes(currentMetrics, previousPayloads)



  // V3 contextual validation
  override def contextPredicates: Map[String, Array[String]] = {
    Map( "TwoVals" -> Array("nationalId", "debtBalance"), "DebtBalanceV" -> Array("debtBalance"), "NationalIdValidation" -> Array("nationalId"), "AccountNumberValidation" -> Array("accountNumber"))
  }

  override def contextuallyValidate(candidateEvents: Array[TaggedValidatedEvent],
                                    contextName: String,
                                    contextualEvents: Array[TaggedCuratedEventWithMap]): Array[(TaggedValidatedEvent, Array[QualityScore], Array[ObjectError])] = {
    if (contextName == "OperationContextualValidation") {
      candidateEvents.map(x => {
        (x, Array(QualityScore("opr", 10.0)), Array(ObjectError("opr", "oprcode", "1", "", "empty")))
      })
    }
    else if (contextName == "ClientContextualValidation") {
      candidateEvents.map(x => {
        (x, Array(QualityScore("cli", 10.0)), Array(ObjectError("cli", "clicode", "1", "", "empty")))
      })
    }
    else {
      candidateEvents.map(x => (x, Array.empty[QualityScore], Array.empty[ObjectError]))
    }
  }

}


