package com.experian.edf.oxygen.validation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.{ApplicationLifecycle, OxygenOrchestratedService}
import com.experian.edf.oxygen.configuration.configs.ValidationElement
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.domain.internal.{Frequency, Lineage, Payload}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.exception.MalformedEventException
import com.experian.edf.oxygen.global.utils.IdGenerator
import com.experian.edf.oxygen.validation.context.ValidationContext
import com.experian.edf.oxygen.validation.domain.ApplicationRunMode._
import com.experian.edf.oxygen.validation.domain.{ValidatedEvent, ValidationProcessor}
import com.experian.edf.oxygen.validator.internal.ObjectError
import com.google.gson.JsonArray
import org.apache.spark.TaskContext
import org.slf4j.LoggerFactory._

import java.util.concurrent.ConcurrentHashMap


/**
 * Companion object to the Validator class. Also serves as a factory class to instantiate different Acceptors.
 * The current phases are
 * Stage 1 -> Validation
 * Stage 2 -> Acceptance
 * Stage 3 -> Curation
 * Stage 4 -> Pinning
 *
 * @see SparkValidator which is the currently supported Validator
 */
object Validator {

  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Validator which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): Validator = {
    create(appContext)
  }

  /**
   * The factory method to create an Validator
   *
   * @param appContext
   * @tparam T
   * @return
   */
  def create[T <: OxygenAppContext](appContext: T): Validator = {
    val config = appContext.config
    if (appContext.isInstanceOf[SparkOxygenAppContext]) {
      val defaultOutputStructure: Boolean = config.configMap.getOrElse("oxygen.features.enable.defaultOutputStructure", "false").toBoolean
      if (config.configMap("oxygen.application.lambda").equalsIgnoreCase("batch"))
        if (defaultOutputStructure) new SparkValidator(appContext.asInstanceOf[SparkOxygenAppContext]) else new SparkValidatorCustomMode(appContext.asInstanceOf[SparkOxygenAppContext])
      else throw new UnsupportedOperationException("only oxygen.application.lambda = batch are supported!")
    } else
      throw new OxygenException("Sorry! I only support SparkOxygenAppContext currently")
  }
}

/**
 * Abstract acceptor class. Current instances are SparkValidator which implements acceptor using Apache Spark.
 * Making the abstract class extend OxygenService gives the
 * The current phases are
 * Stage 1 -> Validation
 * Stage 2 -> Acceptance
 * Stage 3 -> Curation
 * Stage 4 -> Pinning
 *
 * @param appContext The application context which is used by the acceptor.
 * @see SparkValidator
 */
private[oxygen] abstract class Validator(appContext: OxygenAppContext) extends OxygenOrchestratedService(appContext) with ApplicationLifecycle {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val metricsLogger = getLogger("metricsLogger")
  @transient lazy val puLogger = getLogger("puLogger")

  /**
   * This is the method from the acceptors which is exposed to implementations. Create the Validator using an
   * appropriate context and then run the acceptor to start accepting files. @see SparkValidator which is the currently
   * implemented acceptor using Apache Spark
   *
   * @param incremental (Do we want to accept everything or only incrementally)
   * @throws OxygenException
   * @return (Any)
   */
  @throws(classOf[OxygenException])
  def run(incremental: Boolean = true): Any

  // HELPER FUNCTIONS
  private[oxygen] def getProcessorFor(filename: String,
                                      filenamePatterns: List[String],
                                      processorMap: ConcurrentHashMap[String, String]): String = {

    for (pattern <- filenamePatterns) {
      if (filename.matches(pattern))
        return processorMap.get(pattern)
    }
    null.asInstanceOf[String]
  }


  //  def createTaggedValidatedEvent(processor: ValidationProcessor, lineage: Lineage, minFrequency: Frequency, rec: String): TaggedValidatedEvent = {
  //    try {
  //      // Read the raw record from the file and create a quality scored event (ValidatedEvent)
  //      val (validatedEvent, domainData, qualityScore, errorCode) = processor.createValidatedEvent(rec)
  //
  //      // Create event enrichment information from the accepted  and rejected event
  //      val payload = processor.enrichEventWithPayload(validatedEvent)
  //
  //      val eventType = processor.outputEventType
  //
  //      // create eventId to stamp
  //      val eventId = SparkValidator.createEventId(TaskContext.getPartitionId())
  //
  //      // if asOfDate is missing, populate it with receivedDate and set the flag
  //      val (asOfDate, didCreateAsOfDateFromReceivedDate) = if (domainData.asOfDate <= 0) {
  //        (lineage.receivedDate, true)
  //      } else {
  //        (domainData.asOfDate, false)
  //      }
  //
  //      // internal implementation of qualityScored event which wraps record with lineage, enrichment info and quality score
  //      val taggedQualityScoredEvent = new TaggedValidatedEvent(lineage, eventId, validatedEvent.write,
  //        payload, qualityScore, errorCode, eventType, minFrequency, asOfDate, didCreateAsOfDateFromReceivedDate,
  //        domainData.subscriberRelation)
  //
  //      // Return RDD[TaggedValidatedEvent]
  //      taggedQualityScoredEvent
  //
  //    }
  //    catch {
  //      case mef: MalformedEventException => new TaggedValidatedEvent(null.asInstanceOf[Lineage], Long.MinValue, null, null.asInstanceOf[Payload],
  //        Array(QualityScore("", 0)), Array(ObjectError("", "", "", "")), -1, null.asInstanceOf[Frequency], -1, false, "")
  //    }
  //  }


  private[oxygen] def dupCheckForKey(validatedEvent: ValidatedEvent, processor: ValidationProcessor, config: ValidationElement) = {
    val dupCheckEnabled = config.dupKeyEnabled
    val keys =
      if (dupCheckEnabled) {
        val keys = processor.keysForDuplicate(validatedEvent)
        if (keys.isEmpty || keys == null) {
          logger.error("You need to define key(s) for duplicate check. Assertion will fail")
          assert(false)
          ""
        }
        else keys.mkString(":")
      }
      else ""

    keys
  }


  private[oxygen] def createTaggedValidatedEvent(processor: ValidationProcessor, lineage: Lineage,
                                                 minFrequency: Frequency, rec: String, metadata: Map[String, Object], headerObject: Array[ObjectError], footerObject: Array[ObjectError], config: ValidationElement, historicAttributes: Option[JsonArray]=None): Array[TaggedValidatedEvent] = {
    try {
      // Read the raw record from the file and create a quality scored event (ValidatedEvent)

      val validationContext: ValidationContext = ValidationContext(BATCH, historicAttributes, None)
      val taggedEvents = processor.createValidatedEvents(rec, validationContext).map(processedEvent => {
        val (validatedEvent, domainData, qualityScore, errorCode) = (processedEvent.event,
          processedEvent.domainData.get, processedEvent.qualityScores, processedEvent.errors)

        // Create event enrichment information from the accepted  and rejected event
        val payload = processor.enrichEventWithPayload(validatedEvent)

        val eventType = processor.outputEventType

        // create eventId to stamp
        val eventId = IdGenerator.nextEventId(appContext.config.oxygen.JOB_ID, TaskContext.getPartitionId())

        // if asOfDate is missing, populate it with receivedDate and set the flag
        val (asOfDate, didCreateAsOfDateFromReceivedDate) = domainData.asOfDate match {
          case Some(date) => (date, false)
          case None => (lineage.receivedDate, true)
        }

        // If subscriber is present in domain data, populate it else get the same from Lineage.
        // this will be changed in future when subscriber is taken out from Lineage
        val subscriber = domainData.subscriber match {
          case Some(sub) => sub
          case None => lineage.subscriber
        }

        // If datasource is present in domain data, will overwrite Lineage datasource.
        val newLineage = domainData.datasource match {
          case Some(newDataSource) => Lineage(newDataSource,
            lineage.receivedDate,
            lineage.fileName,
            lineage.subscriber,
            lineage.fileAsOfDate,
            lineage.datasetId,
            lineage.recordHash)
          case None => lineage
        }

        val keys = dupCheckForKey(validatedEvent, processor, config)

        // internal implementation of qualityScored event which wraps record with lineage, enrichment info and quality score
        val taggedValidatedEvent = new TaggedValidatedEvent(newLineage, eventId, validatedEvent.write,
          payload, qualityScore, processor.decodeErrorObject(errorCode), eventType, minFrequency, asOfDate, didCreateAsOfDateFromReceivedDate,
          domainData.subscriberRelation, subscriber, headerObject, footerObject, keys, null, processor.fileMetadata.fileTag)

        taggedValidatedEvent


      })
      taggedEvents
    }

    catch {
      case mef: MalformedEventException => Array(new TaggedValidatedEvent(null.asInstanceOf[Lineage], "", null, null.asInstanceOf[Payload],
        Array(QualityScore("", 0)), Array.empty[ObjectError], -1, null.asInstanceOf[Frequency], -1, false, "", "",
        Array.empty[ObjectError], Array.empty[ObjectError]))
    }
  }
}