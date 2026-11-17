package com.experian.edf.oxygen.validation.domain


import com.experian.edf.oxygen.domain.internal.{DomainData, FileMetadata, Lineage, MetricData, Payload}
import com.experian.edf.oxygen.domain.{HistoricAttributes, TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.processors.{OxygenProcessor, ProcessorContext}
import com.experian.edf.oxygen.exception.{MalformedFileException, MalformedHeaderException}
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.validation.context.ValidationContext
import com.experian.edf.oxygen.validation.validator.{ValidationData, ValidationError, ValidationResult, ValidationSuccess}
import com.experian.edf.oxygen.validator.internal.ObjectError
import com.google.gson.JsonArray

/**
  * Created by c15661a on 7/11/17.
  * The abstract class for building a validation processor.
  * Each event type will have a different processor, which is implemented using the reference implementation.
  * The mapping of the event type and processor is defined in the application.conf.  Based on the file name pattern
  * the framework instantiates and calls the applicable processor.
  */



abstract class ValidationProcessor(processorContext: ProcessorContext) extends OxygenProcessor with IValidationProcessor {

  override def createValidatedEvent(rawRecord: String, metadata: Map[String, Object]): (ValidatedEvent, DomainData, Array[QualityScore], Array[ObjectError])
  = createValidatedEvent(rawRecord)

  @deprecated
  def createValidatedEvent(rawRecord: String): (ValidatedEvent, DomainData, Array[QualityScore], Array[ObjectError]) = ???


  /**
   * Method to create multiple events from a single raw record
   * @param rawRecord
   * @return
   */
  @deprecated
  def createValidatedEvents(rawRecord: String): Array[(ValidatedEvent, DomainData, Array[QualityScore], Array[ObjectError])] = {
    Array(createValidatedEvent(rawRecord))
  }

  def keysForDuplicate(rawRecord: ValidatedEvent): Array[Any] = Array.empty


  def listDuplicateEvents(events: List[TaggedValidatedEvent]): List[TaggedValidatedEvent] = events

  /**
   * The createValidatedEventObjects is similar to the regular createValidatedEvent, with the
   * following differences. In addition to the raw record, it also has the optional metadata map which contains all the metadata
   * needed to process each event coming through high frequency . By default this methond calls the createValidatedEvents
   *
   * @param rawRecord
   * @param metadata
   * @return ValidatedEventValueObject which contains all relevant information for the framework to proceed including
   *         the validated event data, the domain data, quality scores, errors and lineage
   */
  @deprecated
  def createValidatedEventObjects(rawRecord: String, metadata: Option[Map[String, Object]] = None): Array[ValidatedEventValueObject] = {
    val metaData = None //Some(createFileMetadata)//
    createValidatedEvents(rawRecord).map(each => {
      new ValidatedEventValueObject(each._1, Some(each._2), each._3, each._4, metaData, Array.empty[ObjectError], Array.empty[ObjectError],Map.empty[String, Int])
    })
  }

  /**
   * todo All the above methods should be soon deprecated once all extensions have migrated
   * The createValidatedEvents is similar to the regular createValidatedEvent, with the
   * following differences. In addition to the raw record, it also has the optional validation context that includes
   * * Application run mode - If the evnt is being processed as batch or stream
   * * HFmetadata map - which contains all the metadata needed to process each event coming through high frequency
   * * Historic attributes - optional Jsonarray of historic attributes based on datasource and subscriber
   * By default this methond calls the createValidatedEventObjects => createValidatedEvents => createValidatedEvent
   *
   * @param rawRecord
   * @param validationContext
   * @return
   */
  def createValidatedEvents(rawRecord: String, validationContext: ValidationContext): Array[ValidatedEventValueObject] = {
    val hfMetadata =validationContext.hfMetadata
    createValidatedEventObjects(rawRecord, hfMetadata)
  }

  override def lookupService: OxygenLookup = processorContext.lookup

  /**
   * Method to validate headers. Override in implementation. Returns ValidatedResult with
   * List[ObjectError] stuffed in. Action on automatic acceptance / rejection will be taken based on
   * configuration.
   *
   * @throws java.lang.Exception
   * @return ValidatedResult
   */
  @deprecated
  @throws(classOf[Exception])
  def validateHeader: ValidationResult = new ValidationSuccess(new ValidationData, Array.empty[ObjectError])

  /**
   * Method to validate headers. Override in implementation. Returns ValidatedResult with
   * List[ObjectError] stuffed in. Action on automatic acceptance / rejection will be taken based on
   * configuration.
   *
   * @param validationContext
   * @throws java.lang.Exception
   * @return
   */
  @throws(classOf[Exception])
  def validateHeader(validationContext: ValidationContext): ValidationResult = {
    validateHeader
  }


  /**
   * Framework calls this extension method if the subscriberRelation is a new relation that it
   * has not seen before.
   * @return
   */
  def validateNewSubscriberRelation(event: TaggedValidatedEvent): Option[(Array[QualityScore], Array[ObjectError])] = None


  /**
   * Method to validate footers. Override in implementation. Returns ValidatedResult with
   * List[ObjectError] stuffed in. Action on automatic acceptance / rejection will be taken based on
   * configuration.
   *
   * @throws java.lang.Exception
   * @return ValidatedResult
   */
  @deprecated
  @throws(classOf[Exception])
  def validateFooter: ValidationResult = new ValidationSuccess(new ValidationData, Array.empty[ObjectError])

  /**
   * Method to validate footers. Override in implementation. Returns ValidatedResult with
   * List[ObjectError] stuffed in. Action on automatic acceptance / rejection will be taken based on
   * configuration.
   *
   * @param validationContext
   * @throws java.lang.Exception
   * @return
   */
  @throws(classOf[Exception])
  def validateFooter(validationContext: ValidationContext): ValidationResult = {
    validateFooter
  }

  /**
   * Method to process an empty file.
   *
   * @throws java.lang.Exception
   * @return ValidatedResult
   */
  @throws(classOf[Exception])
  def validateEmptyFile: ValidationResult = new ValidationError(new ValidationData, Array.empty[ObjectError])

  /**
   * Method to validate filename. Override in implementation. Returns ValidatedResult with
   * List[ObjectError] stuffed in. Action on automatic acceptance / rejection will be taken based on
   * configuration.
   *
   * @throws java.lang.Exception
   * @return ValidatedResult
   */
  @deprecated
  @throws(classOf[Exception])
  def validateFilename: ValidationResult = new ValidationSuccess(new ValidationData, Array.empty[ObjectError])

  /**
   * Method to validate filename. Override in implementation. Returns ValidatedResult with
   * List[ObjectError] stuffed in. Action on automatic acceptance / rejection will be taken based on
   * configuration.
   *
   * @param validationContext
   * @throws java.lang.Exception
   * @return
   */
  @throws(classOf[Exception])
  def validateFilename(validationContext: ValidationContext): ValidationResult = {
    validateFilename
  }

  // Abstract method to create FileMetadata @see{FileMetadata} like # header rows, furnisher, begin date, end date
  @throws(classOf[MalformedFileException])
  def createFileMetadata: FileMetadata

  // Immutable field in framework which holds the file meta data. This MUST BE A LAZY field. The reason for that is as follows
  // If this val is not lazy, it will invoke createFileMetadata as soon as the validation processor is created. At the time of
  // creation of the ValidationProcessor, it is not known whether the header, footer, filename is valid or not valid.
  // If the implementation of createFileMetadata depends on a valid header / footer, the processing will blow up even before the
  // validation begins. By making it lazy, we postpone the invocation of the
  lazy val fileMetadata: FileMetadata = createFileMetadata

  // Package scoped field used within framework to access lineage
  private[oxygen] lazy val eventLineage: Lineage = new Lineage(fileMetadata.dataSource, fileMetadata.receivedDate, processorContext.fileName, fileMetadata.subscriber, fileMetadata.fileAsOfDate)

  /**
   * Implementation specific method to parse the header record and retrieve header information as needed
   *
   * @param record Array[String] header record(s) to parse and retrieve
   * @return Array[String] - Parsed header records as an Array of strings
   */
  @deprecated
  @throws(classOf[MalformedHeaderException])
  def parseHeader(record: Array[String]): Array[String] = Array.empty

  /**
   * Implementation specific method to return a list of fields to enrich a record with, if needed
   * The fields returned by this methods are appended to every event, which can be used later in the pipeline.
   *
   * @return Array[String] : Enriched event fields
   */
  @deprecated
  def enrichEventWith(event: ValidatedEvent): Array[String] = Array.empty

  /**
   * This method replaces enrichEventWith.
   *
   * The framework associates the payload with the event.  This payload is available in subsequent stages
   * of the pipeline.
   *
   * @param event
   * @return Payload. Payload is a Map of Key (of type String) and Value (of type String).
   *
   */
  override def enrichEventWithPayload(event: ValidatedEvent): Payload = new Payload(Map("" -> ""))

  /**
   * Implementation specific method to return true if a row is a header row.
   *
   * @param row String: row to read
   * @return Boolean: Is it a header row?
   */
  def isHeaderRow(row: String): Boolean

  /**
   * Implementation specific method to returns true if a row is a footer row. Default is false
   *
   * @param row String: row to read
   * @return Boolean: Is it a footer row?
   */
  def isFooterRow(row: String): Boolean

  /**
   * If true, this row will be omitted from the validation.  It will not affect any quality score.
   *
   * @param row String: The row data to determine if need filtering out
   * @return Boolean: Should we filter out this row or not. Default is false
   */
  def shouldFilterRow(row: String): Boolean = false

  // def createValidatedEvent(rawRecord: String): (ValidatedEvent, DomainData, Array[QualityScore], Array[ObjectError])

  // The type of event that the processors generates.
  // Event Type is used to find the appropriate class that process this event.
  //  @deprecated
  //  val outputEventType: Int = 0

  /**
   * Method to validate candidate events in context with other events (e.g. historical events, or other related events).
   * Implementation should cast the return records to appropriate type based on the type of the event.
   *
   * @param candidateEvents
   * @param contextualEvents
   * @return
   */
  def contextuallyValidate(candidateEvents: List[TaggedValidatedEvent],
                           contextualEvents: Map[String, List[TaggedCuratedEventWithMap]]): List[(ValidatedEvent, Array[QualityScore], Array[ObjectError])] = ???


  def contextuallyValidate1(candidateEvents: List[TaggedValidatedEvent],
                            contextualEvents: Map[String, List[TaggedCuratedEvent]]): List[(ValidatedEvent, Array[QualityScore], Array[ObjectError])] = ???

  /**
   * Subscriber Relation is an identifier that uniquely identifies the contract between a subscriber and a consumer.
   * In Credit Bureau context it can be the account number of an account.
   * One must be careful in implementing the relation.  In case a subscriber has multiple account types,
   * the unique relation can be expressed as
   * <account type>.<account number>
   *
   * TODO: Add examples
   *
   */
  @deprecated
  def subscriberRelation(pendingValidatedEvent: TaggedValidatedEvent): String = ""


  /**
   * Tells the framework how to perform filtering, grouping, and applying limit on the contextual records.
   *
   * The framework applies filter on DataSource, subscriber (if not null), SubscriberRelation (if not null),
   * performs grouping on name of the predicate.
   *
   * @param pendingTaggedValidatedEvent
   * @return a Map:
   *         with Key as name of the predicate (e.g. "NationalIDValidation", "DueDateValidation")
   *         With Values as a tuple of
   *         Data Source
   *         Subscriber
   *         SubscriberRelation
   *         Limit
   */
  def contextPredicates(pendingTaggedValidatedEvent: TaggedValidatedEvent): Map[String, (String, String, String, Int)] = ???


  // Contextual Validation V3.
  // The following interfaces provides ability to perform contextual validation with history.
  // Based on the context provided, framework returns list of context events for an input event.
  // The context is always within a subscriber.
  /**
   * @return A Map: with Key as the name of the context and list of field names.
   *         These fields must be present in both taggedCuratedEvents and validatedEvents.
   */
  def contextPredicates: Map[String, Array[String]] = Map.empty

  def contextuallyValidate(candidateEvents: Array[TaggedValidatedEvent],
                           contextName: String,
                           contextualEvents: Array[TaggedCuratedEventWithMap]): Array[(TaggedValidatedEvent, Array[QualityScore], Array[ObjectError])] = ???

  /**
   * Gives unique predicate values to the framework to identify a specific File. Predicates should be constructed at a file level and not at a record level.
   * If this method returns None publishHistoricAttributes and validateHistoricAttributes will NOT be called
   *
   * @return
   */
  def createHistoricAttributesPredicates(): Option[(HistoricAttributes, Int)] = None

  /**
   * File level Metrics  that needs to be saved for validating later. Format of the payload is governed by the extension
   *
   * @param data          Data that is to be presisted for future executions.
   * @param previousPayloads Previous payloads for the current file type (Based on DatasetMetricsPredicates defined in createHistoricAttributesPredicates)
   * @return Payload to be saved
   */
  def publishHistoricAttributes(data: Array[Map[String, String]], previousPayloads: Option[List[HistoricAttributes]]): String = ""

  /**
   * Validates Dataset Metrics
   *
   * @param currentAttributes   Data for the current file.
   * @param previousPayloads Previous payloads for the current file type (Based on DatasetMetricsPredicates defined in createHistoryAttributesPredicates)
   * @return ValidatedResult - Defaulted to ValidationSuccess
   */

  @deprecated
  def validateHistoricAttributes(currentAttributes: Array[Map[String, String]], previousPayloads: Option[List[HistoricAttributes]]): ValidationResult = new ValidationSuccess(new ValidationData, Array.empty[ObjectError])

  def validateHistoricAttributes(metricData: MetricData, histoicAttributes: JsonArray): ValidationResult = new ValidationSuccess(new ValidationData, Array.empty[ObjectError])

  /**
   * Workaround function to decode error object for Colombia.  This implementation should be moved to extension.
   * @param objectErrors
   * @return
   */
  def decodeErrorObject(objectErrors: Array[ObjectError]): Array[ObjectError] = {
    objectErrors.map(e => {
      if (e.errorCodeValue.length == 11) {
        val errorCodeName = e.errorCodeValue.substring(6, 9)
        ObjectError(errorCodeName, e.errorCodeValue, e.errorLevel, e.messageKey)
      } else {
        ObjectError(e.errorCodeName, e.errorCodeValue, e.errorLevel, e.messageKey)
      }
    })
  }


}
