package com.experian.edf.oxygen.curation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.{Constants, OxygenOrchestratedService}
import com.experian.edf.oxygen.configuration.configs.CurationRunMode._
import com.experian.edf.oxygen.configuration.configs.{CurationRunMode, OxygenConfig}
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Frequency}
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.global.domain.Datatype
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import com.experian.edf.oxygen.utils.ScalaJacksonUtil.toJson
import com.experian.edf.oxygen.validator.ObjectError
import org.apache.spark.sql.{Encoder, SparkSession}
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

import java.util


/**
 * Added the companion for Curator interface. This can serve as a factory class to instantiate different Curators.
 * The current phases are
 * Stage 1 -> Validation
 * Stage 2 -> Acceptance
 * Stage 3 -> Curation (*this interface*)
 * Stage 4 -> Pinning
 *
 * Current curators are
 *
 * @see SparkCurator
 */
object Curator extends Serializable {


  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Curator which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): Curator = {
    create(appContext)
  }

  // By default, we create a DataCurator which uses Spark
  def create[T <: OxygenAppContext](appContext: T): Curator = {
    val config = appContext.config
    appContext match {
      case context: SparkOxygenAppContext =>
        if (config.configMap("oxygen.application.lambda").equalsIgnoreCase("stream")) new SparkHighFrequencyCurator(context)
        else new SparkCurator(context)
      case _ => throw new OxygenException("Currently supported context is SparkOxygenAppContext")
    }
  }

  def curateEvent(processor: CurationProcessor, taggedValidatedEvent: TaggedValidatedEvent, spark: SparkSession, oxygenConf:OxygenConfig): TaggedCuratedEventWithMap = {
    val includeQualityCodes = oxygenConf.oxygen.stage.curation.get.includeQualityCodes
    val includeErrorCodes = oxygenConf.oxygen.stage.curation.get.includeErrorCodes
    implicit val taggedCuratedEventEncoder: Encoder[TaggedCuratedEventWithMap] = org.apache.spark.sql.Encoders.kryo[TaggedCuratedEventWithMap]

    val qualityScores = if (includeQualityCodes) taggedValidatedEvent.qualityScores else null
    val errorCodes = if (includeErrorCodes) taggedValidatedEvent.errorCodes else null

    // Read out lineage
    val lineage = taggedValidatedEvent.lineage

    //    val eventType = taggedValidatedEvent.eventType
    // get the eventId
    val eventId = taggedValidatedEvent.eventId

    // Read in a record from the accepted files and create an accepted event. Implementation will provide this method
    //val acceptedEvent = processor.readAcceptedEvent(taggedAcceptedEvent.acceptedRecord)
    // Pass in the accepted event to processor and bring back the curated event. Implementation will provide this method
    val curatedEvent: CuratedEvent = processor.curateEvent(taggedValidatedEvent)

    val eventType = processor.outputEventType

    // get the payload
    val payload = processor.enrichEventWithPayload(taggedValidatedEvent)

    // get lookup context
    //val lookupContext = processor.createContextIndex(taggedAcceptedEvent)

    // get the subscriberRelation
    val subscriberRelation = processor.subscriberRelation(taggedValidatedEvent)

    // get the subscriberRelation Start OxygenEpochDate
    val subscriberRelationStartDate = processor.subscriberRelationStartDate(taggedValidatedEvent)

    // get the subscriberRelation End OxygenEpochDate
    val subscriberRelationEndDate = processor.subscriberRelationEndDate(taggedValidatedEvent)

    val inferredAsDeleted = processor.inferredAsDeleted(taggedValidatedEvent)

    // Wrap around the curated record with lineage and eventId to write to file
    val taggedCuratedEventWithMap = new TaggedCuratedEventWithMap(lineage, eventId,
      ScalaJacksonUtil.fromJson(curatedEvent.write, classOf[java.util.Map[String, Any]]).get, eventType, payload, taggedValidatedEvent.minFrequency,
      taggedValidatedEvent.asOfDate, taggedValidatedEvent.didCreateAsOfDateFromReceivedDate, taggedValidatedEvent.subscriber, subscriberRelation,
      subscriberRelationStartDate, subscriberRelationEndDate, false, inferredAsDeleted, taggedValidatedEvent.fileTag,
      taggedValidatedEvent.notificationLoad, qualityScores, errorCodes)

    // Return the tagged curated event: println(curatedEvent)
    taggedCuratedEventWithMap
  }


}

/**
 * Different phases of the pipeline are implemented within the framework
 * The current phases are
 * Stage 1 -> Validation
 * Stage 2 -> Acceptance
 * Stage 3 -> Curation
 * Stage 4 -> Pinning
 *
 * @param appContext OxygenAppContext for this class
 */
abstract class Curator(appContext: OxygenAppContext) extends OxygenOrchestratedService(appContext) {

  @transient lazy val logger: Logger = getLogger(this.getClass)
  @transient lazy val metricsLogger: Logger = getLogger(Constants.METRICS_LOGGER)
  @transient lazy val puLogger: Logger = getLogger(Constants.PROCESS_UPDATES_LOGGER)
  val includeQualityCodes = oxygenConf.oxygen.stage.curation.get.includeQualityCodes
  val includeErrorCodes = oxygenConf.oxygen.stage.curation.get.includeErrorCodes

  val KafkaMsg = new KafkaMsg(oxygenConf)
  /**
   * This method is deprecated. Use Curator.run instead
   *
   * @param incremental to run in incremental or stock mode
   * @throws OxygenException
   * @return
   */
  @Deprecated
  @throws(classOf[OxygenException])
  def run(incremental: Boolean): Any = {
    logger.error("Curator.run(incremental:Boolean) has been deprecated. Please use Curator.run instead. This method will be removed in a future release. Stock/flow can be configured using the parameter oxygen.stage.curation.performStockCuration = true|false. In current setting oxygen.stage.curation.performStockCuration parameter is ignored.")
    if (incremental) runInternal(FLOW) else runInternal(STOCK)
  }

  @throws(classOf[OxygenException])
  protected def runInternal(runMode: CurationRunMode): Any

  @throws(classOf[OxygenException])
  def run(): Any = runInternal(appContext.config.oxygen.stage.curation.get.runMode)

  def curateTaggedEvent(processor: CurationProcessor,
                        taggedValidatedEvent: TaggedValidatedEvent,
                        domainDictionarType: Option[Map[String, Datatype]],
                        changeCurationFrequency: Boolean): TaggedCuratedEventWithMap = {
    val curatedEvent: CuratedEvent = processor.curateEvent(taggedValidatedEvent)
    val curatedEventAsMap = OxygenCollectionsUtils.asJava(ScalaJacksonUtil.fromJson(curatedEvent.write, classOf[Map[String, Any]]).get)

    val newFrequency = if (changeCurationFrequency) {
      getFrequency(curatedEventAsMap, taggedValidatedEvent)
    }
    else {
      taggedValidatedEvent.minFrequency
    }

    val qualityScores = if (includeQualityCodes) taggedValidatedEvent.qualityScores else null
    val errorCodes = if (includeErrorCodes) taggedValidatedEvent.errorCodes else null

    val taggedCuratedEventWithMap = TaggedCuratedEventWithMap(
      lineage = taggedValidatedEvent.lineage,
      eventId = taggedValidatedEvent.eventId,
      curatedRecordJson = curatedEventAsMap,
      eventType = processor.outputEventType,
      payload = processor.enrichEventWithPayload(taggedValidatedEvent),
      minFrequency = newFrequency,
      asOfDate = taggedValidatedEvent.asOfDate,
      didCreateAsOfDateFromReceivedDate = taggedValidatedEvent.didCreateAsOfDateFromReceivedDate,
      subscriber = taggedValidatedEvent.subscriber,
      subscriberRelation = processor.subscriberRelation(taggedValidatedEvent),
      subscriberRelationStartDate = processor.subscriberRelationStartDate(taggedValidatedEvent),
      subscriberRelationEndDate = processor.subscriberRelationEndDate(taggedValidatedEvent),
      inferredAsDeleted = taggedValidatedEvent.inferredAsDeleted,
      fileTag = taggedValidatedEvent.fileTag,
      notificationLoad = taggedValidatedEvent.notificationLoad,
      qualityScores = qualityScores,
      errorCodes = errorCodes
    )
    taggedCuratedEventWithMap
  }

  def getFrequency(curatedEventAsMap: util.Map[String, Any], taggedValidatedEvent: TaggedValidatedEvent): Frequency = {
    if (curatedEventAsMap.containsKey(Constants.REC_NEW_FREQUENCY)) {
      val newFreq = curatedEventAsMap.get(Constants.REC_NEW_FREQUENCY).asInstanceOf[Map[String, Any]]
      val frequencyUnit = newFreq.get(Constants.UNIT).get.asInstanceOf[Map[String, Any]].get(Constants.NAME).get.asInstanceOf[String]
      val count = newFreq.get(Constants.COUNT).get.asInstanceOf[Int]
      Frequency.getFrequency(count, frequencyUnit)
    }
    else taggedValidatedEvent.minFrequency
  }
}