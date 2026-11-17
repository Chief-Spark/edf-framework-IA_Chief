package com.experian.edf.oxygen.acceptance.engines.hf

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import org.apache.spark.sql.streaming.{DataStreamWriter, Trigger}
import org.apache.spark.sql.{DataFrame, ForeachWriter, SparkSession}
import org.slf4j.LoggerFactory.getLogger

private[oxygen] object HfConfigs {
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def getTriggerOption(processingTriggerOption: String, intervalTime: String,
                                       eventStream: DataStreamWriter[(String)]) = {
    if (processingTriggerOption == "continuous") {
      logger.debug(s"Trigger option is set for continuous")
      eventStream
        .trigger(Trigger.Continuous(intervalTime))
    }
    else {
      logger.debug(s"Trigger option is set for microBatch")
      eventStream
        .trigger(Trigger.ProcessingTime(intervalTime))
    }
  }

  private[oxygen] def getTriggerOption(processingTriggerOption: String, intervalTime: String,
                                       eventStream: Either[DataStreamWriter[String], DataStreamWriter[(String, Long)]]) = {
    eventStream match {
      case Left(regEventStream) =>
        if (processingTriggerOption == "continuous") {
          logger.debug(s"Trigger option is set for continuous")
          regEventStream
            .trigger(Trigger.Continuous(intervalTime))
        }
        else {
          logger.debug(s"Trigger option is set for microBatch")
          regEventStream
            .trigger(Trigger.ProcessingTime(intervalTime))
        }

      case Right(streamWithCurationTime) =>
        if (processingTriggerOption == "continuous") {
          logger.debug(s"Trigger option is set for continuous")
          streamWithCurationTime
            .trigger(Trigger.Continuous(intervalTime))
        }
        else {
          logger.debug(s"Trigger option is set for microBatch")
          streamWithCurationTime
            .trigger(Trigger.ProcessingTime(intervalTime))
        }
    }
  }

  /**
   * Create the input config for Kafka Source
   *
   * @return Map[String, String]
   */
  private[oxygen] def createInputConfig(configMap: Map[String, String], sourceTopic: String) = {
    val newInputConfig = configMap + ("subscribe" -> sourceTopic)
    newInputConfig
  }

  private[oxygen] def createInputConfig(configMap: Map[String, String], sourceTopic: String, sourceTopicOffset: String): Map[String, String] = {
    val newInputConfig = configMap + ("subscribe" -> sourceTopic) + ("startingOffsets" -> sourceTopicOffset)
    newInputConfig
  }

  private[oxygen] def createOutputConfig(configMap: Map[String, String], targetTopic: String, checkPointLocation: String) = {
    val newInputConfig = configMap + ("topic" -> targetTopic, "checkpointLocation" -> checkPointLocation)
    newInputConfig
  }

  private[oxygen] def createInputStream(sparkSession: SparkSession, configMap: Map[String, String]): DataFrame = {
    val inputStream = sparkSession.readStream
      .format("kafka")
      .options(OxygenCollectionsUtils.asJava(configMap))
      .load

    inputStream
  }

}
