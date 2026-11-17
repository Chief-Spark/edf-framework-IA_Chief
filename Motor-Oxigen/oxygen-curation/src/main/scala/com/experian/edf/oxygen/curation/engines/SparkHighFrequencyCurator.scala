package com.experian.edf.oxygen.curation.engines

import com.experian.edf.oxygen.curation.engines.hf.HfMessage
import com.experian.edf.oxygen.curation.engines.notification.HfNotifications
import com.experian.edf.oxygen.curation.engines.stategeneration.CuratedEventVO
import com.experian.edf.oxygen.curation.domain.HFErrorEvent
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.stages.CurationElement
import com.experian.edf.oxygen.configuration.configs.typesafe.ProcessElement
import com.experian.edf.oxygen.configuration.configs.{CurationRunMode, OxygenConfig}
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.module.logging.metrics.MetricsConstants
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.utils.JsonUtils
import org.apache.spark.sql._
import org.apache.spark.sql.streaming.DataStreamWriter
import org.slf4j.LoggerFactory._

import java.io.File
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

class SparkHighFrequencyCurator(appContext: SparkOxygenAppContext) extends Curator(appContext) {

  private val spark = appContext.sparkSession
  private val config = appContext.config
  private val source = config.configMap("oxygen.stage.curation.source")
  private val target = config.configMap("oxygen.stage.curation.target")
  private val error = config.configMap("oxygen.stage.curation.error")
  private val successTargetCheckpointLocation = config.configMap("oxygen.stage.curation.checkpointLocation")
  private val errorCheckpointLocation = config.configMap("oxygen.stage.curation.checkpointLocation") + File.separator + "error"

  @transient override lazy val logger = getLogger(this.getClass)


  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext("HFCuration", "HFCurator", None, None, oxygenConf)


  override def runInternal(runMode: CurationRunMode): Any = {

    val curationConfig = oxygenConf.oxygen.stage.curation.get.configs // curation config section from config file

    val inputStreamKafkaConfigMap = createInputConfig()
    val outputStreamKafkaConfigMap = createOutputConfig(target, successTargetCheckpointLocation)
    val errorStreamKafkaConfigMap = createOutputConfig(error, errorCheckpointLocation)
    val kafkaMsg = spark.sparkContext.broadcast(new KafkaMsg(config)).value

    logger.debug(source + " " + target + " " + error + " " + successTargetCheckpointLocation + "  " + errorCheckpointLocation)

    implicit val taggedCuratedEventEncoder1 = org.apache.spark.sql.Encoders.kryo[TaggedCuratedEventWithMap]

    val loadedProcessors = SparkHighFrequencyCurator.mapProcessor(curationConfig)

    val inputStream = createInputStream(inputStreamKafkaConfigMap).selectExpr("CAST(value AS STRING)", "topic", "partition", "offset")

    val curatedEvent = SparkHighFrequencyCurator.runEachEvent(error: String, kafkaMsg, spark, inputStream, loadedProcessors, config)


    implicit val taggedCuratedEventEncoder = org.apache.spark.sql.Encoders.kryo[CuratedEventVO]
    implicit val errorEventEncoder = org.apache.spark.sql.Encoders.kryo[HFErrorEvent]


    val curatedEventsStream = createOutputStream(curatedEvent.filter(each => each.error == null).map(each => each.event), outputStreamKafkaConfigMap)
    curatedEventsStream.start()

    //    val errorEventStream = createErrorStream(curatedEvent.filter(each => each.error != null).map(each => each.error), errorStreamKafkaConfigMap)
    //    errorEventStream.start

    spark.streams.awaitAnyTermination()
  }


  /**
   * Create the input config for Kafka Source
   *
   * @return Map[String, String]
   */
  def createInputConfig() = {
    val inputStreamKafkaConfigMap = oxygenConf.oxygen.SPARK_STRUCTURED_CONSUMER_PROPERTIES
    val newInputConfig = inputStreamKafkaConfigMap + ("subscribe" -> source)
    newInputConfig
  }

  /**
   * Create the output config for Kafka target
   *
   * @return Map[String, String]
   */
  def createOutputConfig(destination: String, checkPoint: String) = {
    val outputStreamKafkaConfigMap = oxygenConf.oxygen.SPARK_STRUCTURED_PRODUCER_PROPERTIES
    val newInputConfig = outputStreamKafkaConfigMap ++ Map("topic" -> destination, "checkpointLocation" -> checkPoint)
    newInputConfig
  }

  /**
   * Create the input stream based DataFrame on the configuration options in the config file
   *
   * @return
   */
  def createInputStream(config: Map[String, String]): DataFrame = {

    // Retrieve all the configurations which refer to the inputStream prefixed by "input." in the config file
    // Apply then as the options to the DataStreamReader and then create the dataframe and return it
    val inputStream = spark.readStream
      .format("kafka").options(config).load

    inputStream
  }

  /**
   * Create the output stream based on the configuration options in the config file
   *
   * @return
   */
  private def createOutputStream(taggedValidatedEvent: Dataset[TaggedCuratedEventWithMap], config: Map[String, String]): DataStreamWriter[String] = {

    import spark.implicits._
    val outputStream = taggedValidatedEvent.map(each => JsonUtils.toJson(each))
      .writeStream
      .format("kafka")
      .options(config)
    outputStream
  }


  def createErrorStream(events: Dataset[HFErrorEvent], config: Map[String, String]): DataStreamWriter[String] = {

    import spark.implicits._
    val outputStream = events.map(each => JsonUtils.toJson(each))
      .writeStream
      .format("kafka")
      .options(config)
    outputStream
  }

}

private[oxygen] object SparkHighFrequencyCurator extends Serializable {

  val processorCache = new ConcurrentHashMap[String, CurationProcessor]()


  implicit def tuple2[A1, A2](
                               implicit e1: Encoder[TaggedCuratedEvent],
                               e2: Encoder[HFErrorEvent]
                             ): Encoder[(TaggedCuratedEvent, HFErrorEvent)] = Encoders.tuple[TaggedCuratedEvent, HFErrorEvent](e1, e2)

  implicit val taggedCuratedEventEncoder1 = org.apache.spark.sql.Encoders.kryo[CuratedEventVO]
  implicit val errorEventEncoder = org.apache.spark.sql.Encoders.kryo[HFErrorEvent]


  private def runEachEvent(errorTopic: String, kafkaMsg: KafkaMsg, spark: SparkSession, inputStream: DataFrame,
                           loadedProcessors: Map[String, CurationProcessor], config:OxygenConfig) = {

    val curatedEvent = inputStream.mapPartitions(recIter => {
      recIter
        .map(r =>
          (r.getAs[String]("value"), r.getAs[String]("topic"), r.getAs[Timestamp]("timestamp"), r.getAs[Int]("partition"))).
        map(r => {
          try {
            val curationStartTime = System.currentTimeMillis()

            val (inputMsg, inputTopic, timestamp, kPartition) = (r._1, r._2, r._3, r._4)
            val taggedValidatedEvent = JsonUtils.fromJson(inputMsg, classOf[TaggedValidatedEvent])

            val processorName = taggedValidatedEvent.lineage.fileName
            val cachedProcessor = processorCache.get(processorName)
            val processor = if (cachedProcessor == null) {
              val found = loadedProcessors.find(each => processorName.toString matches each._1).get._2
              processorCache.put(processorName, found)
              found
            } else cachedProcessor


            val cev = CuratedEventVO(
              Curator.curateEvent(processor, taggedValidatedEvent, spark, config),
              inputMsg,
              inputTopic,
              null,
              timestamp,
              kPartition,
              curationStartTime)

            HfNotifications.sendNotification(kafkaMsg, cev.event.notificationLoad, Constants.CURATION, Constants.SUCCESS)

            cev
          }
          catch {
            case ex: Exception => {
              val notificationLoad = HfNotifications.getNotificationOnErrorHandleTcem(r._1)
              val (inputMsg, inputTopic, timestamp) = (r._1, r._2, r._3)
              HfMessage.sendErrorStackTrace(kafkaMsg, inputTopic, MetricsConstants.Actions.STATEGENERATION,
                inputMsg, ex.getMessage, ex.getStackTrace.mkString(""),
                errorTopic)
              CuratedEventVO(null, inputMsg, inputTopic, HFErrorEvent(ex.getMessage, inputTopic, inputMsg, ex.getStackTrace.mkString("")), timestamp)
            }
          }
        })
    })
    curatedEvent
  }

  /**
   * Map each processor for each pattern
   *
   * @return
   */

  private def mapProcessor(curationConfig: Map[String, ProcessElement]) = {
    val curationMap = curationConfig.keys.map(eachKey => {
      val curationProcessorContext = CurationProcessorContext(OxygenLookupService.getServiceB.value)
      val config = curationConfig(eachKey).asInstanceOf[CurationElement]
      val processor = config.processor.get.getConstructor(curationProcessorContext.getClass)
        .newInstance(curationProcessorContext).asInstanceOf[CurationProcessor]
      eachKey -> processor
    }).toMap

    curationMap
  }
}