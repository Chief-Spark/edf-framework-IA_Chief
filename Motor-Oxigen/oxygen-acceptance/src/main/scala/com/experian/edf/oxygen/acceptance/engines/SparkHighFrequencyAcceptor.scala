package com.experian.edf.oxygen.acceptance.engines

import java.io.File
import java.sql.Timestamp
import java.util.{Collections, Properties}
import java.util.concurrent.ConcurrentHashMap
import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.acceptance.engines.hf.{HfConfigs, HfMessage, HfNotifications}
import com.experian.edf.oxygen.acceptance.util.RecordValidationParser
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.AcceptanceElement
import com.experian.edf.oxygen.configuration.configs.typesafe.ProcessElement
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.internal.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.processors.HfMessageProcessor
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.module.logging.metrics.MetricsConstants
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.utils._
import org.apache.kafka.clients.consumer.{KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.spark.sql._
import org.apache.spark.sql.streaming.{DataStreamWriter, OutputMode}
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable


/**
 * Oxygen Acceptance stage:
 * 1. Get all validated records for each file - update processdb status
 * 2. Calculate metrics
 * 3. Check File Acceptance criteria and File Rejection criteria (Both cannot be True) (criteria includes processdb external action)
 * 4. For accepted files: Filter records based on Record Acceptance criteria
 * 5. Accepted files go to Acceptance/Accepted
 * 6. Rejected files go to Acceptance/Rejected/RejectedFiles
 * 7. Rejected records go to Acceptance/Rejected/RejectedRecords
 * 8. Update processdb and log files
 */


/**
 * Created by c20286a on 7/24/17.
 * This class is the implementation of the Acceptor, when Apache Spark is the running engine.
 * It accepts the SparkOxygenAppContext object which has been created by the implementation.
 *
 */

private[oxygen] case class HFAcceptanceErrorEvent(error: String, topic: String, value: String, stackTrace: Array[StackTraceElement]) extends Serializable

private[oxygen] case class EventAcceptedVO(event: TaggedValidatedEvent,
                                           isValid: Boolean,
                                           error: HFAcceptanceErrorEvent,
                                           timestamp: Timestamp) extends Serializable

//case class Metric( accepted: Double, rejected: Double)


class SparkHighFrequencyAcceptor(appContext: SparkOxygenAppContext) extends Acceptor(appContext) {

  @transient override lazy val logger = getLogger(this.getClass)

  private val config = appContext.config
  private val spark = appContext.sparkSession
  private val savingFileFormat = appContext.config.oxygen.FILE_STORAGE_FORMAT
  private val source = config.configMap("oxygen.stage.acceptance.source")
  private val targetAccepted = config.configMap("oxygen.stage.acceptance.targetAccepted")
  private val targetRejected = config.configMap("oxygen.stage.acceptance.targetRejected")
  private val error = config.configMap("oxygen.stage.acceptance.error")
  private val sendRejectMsg = true

  private val checkpointLocationAccepted = config.configMap("oxygen.stage.acceptance.paths.acceptedCheckpointLocation")
  private val checkpointLocationRejected = config.configMap("oxygen.stage.acceptance.paths.rejectedCheckpointLocation")
  private val errorCheckpointLocation = config.configMap("oxygen.stage.acceptance.paths.rejectedCheckpointLocation") + File.separator + "error"
  private val hfAcceptanceOutputDir = oxygenConf.oxygen.APP_HOME + File.separator + Constants.ACCEPTANCE + File.separator + Constants.uniqueDirNameAcceptance + File.separator // Acceptance output DIR
  private val hfRejectOutputDir = oxygenConf.oxygen.APP_HOME + File.separator + Constants.ACCEPTANCE + File.separator + Constants.uniqueDirNameReject + File.separator // Acceptance output DIR

  private val batchTriggerTime = config.configMap("oxygen.stage.acceptance.batchTriggerTime")
  private val batchCheckpointLocation = config.configMap("oxygen.stage.acceptance.paths.checkpointLocation")
  private val batchCheckpointLocationAccepted = batchCheckpointLocation + File.separator + "HFAccepted"
  private val batchCheckpointLocationRejected = batchCheckpointLocation + File.separator + "HFRejected"
  private val kafkaMsg = spark.sparkContext.broadcast(new KafkaMsg(config)).value
  private val enableHFMonitoring = oxygenConf.oxygen.isFeatureActive(Constants.features.ENABLE_HF_PROCESS_MONITORING)

  private val processingTriggerOption = config.oxygen.HF_TRIGGER_OPTION
  private val intervalTime = config.oxygen.HF_TRIGGER_INTERVAL_TIME
  private val hfMsgProcessor: HfMessageProcessor = HfMessageProcessor.create(config.oxygen.HF_MESSAGE_PROCESSOR, config.oxygen.HF_MESSAGE_PROCESSOR_CONF)
  // Switch Over properties
  private val hfWriteToSwitchOverConsumer = config.oxygen.isFeatureActive(Constants.features.ENABLE_WRITE_SWITCH_OVER_CONSUMER)
  private val switchOverKafkaConsumerProp = oxygenConf.oxygen.SPARK_STRUCTURED_SWITCHOVER_CONSUMER_PROPERTIES
  private val props: Properties = new Properties
  for (each <- switchOverKafkaConsumerProp) props.put(each._1, each._2)

  private[oxygen] object KafkaObject {
    private[oxygen] val switchOverConsumer: KafkaConsumer[String, String] = new KafkaConsumer[String, String](props)
  }

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext("HFAcceptance", "HFAcceptor", None, None, oxygenConf, Option(appContext.sc.applicationId))

  @throws(classOf[OxygenException])
  @throws(classOf[StringIndexOutOfBoundsException])
  @throws(classOf[MatchError])
  @throws(classOf[IllegalArgumentException])
  override def run(incremental: Boolean = true): Any = {

    if (enableHFMonitoring) HFmonitoring.hfStreamListener(appContext, MetricsConstants.Actions.ACCEPTANCE, spark)

    val inputStreamKafkaConfigMap = HfConfigs.createInputConfig(oxygenConf.oxygen.SPARK_STRUCTURED_CONSUMER_PROPERTIES, source)
    val outputStreamAcceptedKafkaConfigMap = HfConfigs.createOutputConfig(oxygenConf.oxygen.SPARK_STRUCTURED_PRODUCER_PROPERTIES
      , targetAccepted, checkpointLocationAccepted)

    logger.debug("kafka source is " + source + " target is " + targetAccepted + "check Location is + " + checkpointLocationAccepted + " writeToSwitchOverConsumer:" + hfWriteToSwitchOverConsumer)

    val config = oxygenConf.oxygen.acceptanceValues
    val acceptorMap = SparkHighFrequencyAcceptor.putAcceptanceExpression(config, appContext)

    import spark.implicits._
    implicit val eventEncoder = org.apache.spark.sql.Encoders.kryo[EventAcceptedVO]
    implicit val errorEventEncoder = org.apache.spark.sql.Encoders.kryo[HFAcceptanceErrorEvent]


    val inputStream = HfConfigs.createInputStream(spark, inputStreamKafkaConfigMap)
      .selectExpr("CAST(value AS STRING)", "topic", "partition", "offset", "CAST(key AS STRING)", "timestamp")

    val validatedEvents = validateEachEvent(acceptorMap, inputStream, sendRejectMsg)

    // accepted records in hf
    val acceptedStream = validatedEvents.filter(each => each.error == null)
      .filter(each => each.isValid == true)
      .map(each => each.event)
      .map(each => hfMsgProcessor.postprocess(JsonUtils.toJson(each)))
      .writeStream.format("kafka")
      .outputMode(OutputMode.Update())
      //      .options(outputStreamAcceptedKafkaConfigMap)
      .options(OxygenCollectionsUtils.asJava(outputStreamAcceptedKafkaConfigMap))

    HfConfigs.getTriggerOption(processingTriggerOption, intervalTime, Left(acceptedStream))
      .start

    // saving to files
    //    val saveFormat = getSavingFileFormat(savingFileFormat)
    //
    //    // write accepted record to a file
    //    val acceptedEventStream = createFileStream(validatedEvents.filter(each => each.error == null)
    //      .filter(each => each.isValid == true)
    //      .map(each => each.event.toString), saveFormat, hfAcceptanceOutputDir, batchCheckpointLocationAccepted, batchTriggerTime)
    //
    //    // write rejected record to a file
    //    val rejectedEventStream = createFileStream(validatedEvents.filter(each => each.error == null)
    //      .filter(each => each.isValid == false)
    //      .map(each => each.event.toString), saveFormat, hfRejectOutputDir, batchCheckpointLocationRejected, batchTriggerTime)
    //
    //    acceptedEventStream.start
    //    rejectedEventStream.start

    spark.streams.awaitAnyTermination()
  }

  /**
   * validate each event
   *
   * @param acceptorMap : file pattern and its expression map
   * @param inputStream : Dataframe from kafka input stream
   * @return Dataset[(TaggedValidatedEvent, Boolean)]
   */

  def validateEachEvent(acceptorMap: ConcurrentHashMap[String, Option[String]], inputStream: DataFrame, sendRejectMsg: Boolean) = {
    //import spark.implicits._
    implicit val eventEncoder = org.apache.spark.sql.Encoders.kryo[EventAcceptedVO]
    implicit val errorEventEncoder = org.apache.spark.sql.Encoders.kryo[HFAcceptanceErrorEvent]

    val event = inputStream.mapPartitions(recIter => {

      recIter
        .map(r => (
          r.getAs[String]("value"),
          r.getAs[String]("topic"),
          r.getAs[String]("key"),
          r.getAs[Int]("partition"),
          r.getAs[Long]("offset"),
          r.getAs[Timestamp]("timestamp")
        ))
        .map(rec => {
          try {
            val preprocessedEvent = hfMsgProcessor.preprocess(rec._1)
            val (eventValue, topic, key, partition, offset, timestamp) = (preprocessedEvent, rec._2, rec._3, rec._4, rec._5, rec._6)

            if (hfWriteToSwitchOverConsumer) {
              KafkaObject.switchOverConsumer.commitSync(Collections.singletonMap(new TopicPartition(topic, partition), new OffsetAndMetadata(offset + 1)))
            }
            val taggedValidatedEvent: TaggedValidatedEvent = JsonUtils.fromJson(eventValue, classOf[TaggedValidatedEvent])

            val lineageFilename = taggedValidatedEvent.lineage.fileName
            val cachedExpression = SparkHighFrequencyAcceptor.acceptanceCache.get(lineageFilename)
            val namePatten = if (cachedExpression == null) {
              val found = oxygenConf.oxygen.acceptanceValues.find(each => lineageFilename matches each._1).get._2.asInstanceOf[AcceptanceElement].recordAcceptance

              SparkHighFrequencyAcceptor.acceptanceCache.put(lineageFilename, found)
              lineageFilename
            } else lineageFilename

            val recordAcceptanceExpression = SparkHighFrequencyAcceptor.acceptanceCache.get(namePatten)
            val isValid = SparkHighFrequencyAcceptor.isRecordActionValid(taggedValidatedEvent, recordAcceptanceExpression)
            val notificationLoad = taggedValidatedEvent.notificationLoad

            if (isValid) {
              HfNotifications.sendNotification(kafkaMsg, notificationLoad, Constants.ACCEPTANCE, Constants.SUCCESS)
            }
            else {
              if (sendRejectMsg) kafkaMsg.sendMesageHf(targetRejected, hfMsgProcessor.postprocess(taggedValidatedEvent.toString), "")
              HfNotifications.sendNotification(kafkaMsg, notificationLoad, Constants.ACCEPTANCE, Constants.REJECT)
            }
            EventAcceptedVO(taggedValidatedEvent, isValid, null, timestamp)
          }
          catch {
            case ex: Exception => {

              val (inputMsg, topic, timestamp) = (rec._1, rec._2, rec._6)
              val notificationLoad = HfNotifications.getNotificationOnErrorHandleTve(inputMsg)
              HfNotifications.sendNotification(kafkaMsg, notificationLoad, Constants.ACCEPTANCE, Constants.ERROR)

              HfMessage.sendErrorStackTrace(kafkaMsg, topic, MetricsConstants.Actions.ACCEPTANCE, inputMsg, ex.getMessage, ex.getStackTrace.mkString(""), error)

              EventAcceptedVO(null, false, HFAcceptanceErrorEvent(ex.getMessage, topic, inputMsg, ex.getStackTrace), timestamp)
            }

          }
        })
    })
    event
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
  def createOutputConfig(target: String, checkPoint: String) = {
    val outputStreamKafkaConfigMap = oxygenConf.oxygen.SPARK_STRUCTURED_PRODUCER_PROPERTIES
    val newInputConfig = outputStreamKafkaConfigMap ++ Map("topic" -> target, "checkpointLocation" -> checkPoint)
    newInputConfig
  }

  def createErrorConfig = {
    val outputStreamKafkaConfigMap = oxygenConf.oxygen.SPARK_STRUCTURED_PRODUCER_PROPERTIES
    val newInputConfig = outputStreamKafkaConfigMap ++ Map("topic" -> error, "checkpointLocation" -> errorCheckpointLocation)
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
  def createOutputStream(taggedValidatedEvent: Dataset[TaggedValidatedEvent], config: Map[String, String]): DataStreamWriter[String] = {

    import spark.implicits._
    val outputStream = taggedValidatedEvent.map(each => JsonUtils.toJson(each))
      .writeStream
      .format("kafka")
      .options(config)
    outputStream
  }

}


private[oxygen] object SparkHighFrequencyAcceptor extends Serializable {

  @transient lazy val logger = getLogger(this.getClass)
  val acceptanceCache = new ConcurrentHashMap[String, Option[String]]()

  val isRecordActionValid: (TaggedValidatedEvent, Option[String]) => Boolean = (record, recordExpression) => {
    if ((recordExpression match {
      case None => false
      case default => true
    }) && !recordExpression.get.isEmpty) {
      val recordParser = new RecordValidationParser(Left(record))
      val out = recordParser.parseAll(recordParser.boolExpr, recordExpression.get).get
      out
    }
    else
      true
    // TODO:  check this else
  }

  /**
   * Map each acceptance expression for each topic
   *
   * @return
   */
  def putAcceptanceExpression(acceptanceConfig: mutable.Map[String, ProcessElement], appContext: SparkOxygenAppContext) = {
    val acceptanceExpressionMap = new ConcurrentHashMap[String, Option[String]]()

    acceptanceConfig.foreach(elem => {
      val recordExpression = elem._2.asInstanceOf[AcceptanceElement].recordAcceptance
      acceptanceExpressionMap.put(elem._1, recordExpression)
    })
    acceptanceExpressionMap
  }


}