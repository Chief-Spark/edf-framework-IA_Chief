package com.experian.edf.oxygen.acceptance.engines.file

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.common.messaging.KafkaMessaging
import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import org.slf4j.LoggerFactory.getLogger

import java.io.FileWriter

private[oxygen] class KafkaMsg(oxygenConfig: OxygenConfig) extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  @transient lazy val kafkaMessage = new KafkaMessaging(oxygenConfig.oxygen.getKafkaProducerProperties, oxygenConfig.oxygen.getKafkaConsumerProperties, None, None, 3, 1);

  def sendRecordWithProperties(config: OxygenConfig, kafkaTopic: String, msg: String, fileId: String) = {
    if(msg != null && !msg.trim.isEmpty) {
      if (oxygenConfig.oxygen.WRITE_TO_FILE_ENABLED) {
        val pathWithFileId = oxygenConfig.oxygen.PATH_TO_WRITE.get + "/" + kafkaTopic
        val fw = new FileWriter(pathWithFileId, true)
        try {
          fw.write(msg)
        }
        finally fw.close()
      }

      if (config.oxygen.KAFKA_ENABLED) {
        try {
          kafkaMessage.send(Option(kafkaTopic), Option(msg), Some(fileId))
        }
        catch {
          case ir: InterruptedException => throw new OxygenException("Exception on Kafka Message ", ir)
          case n: NullPointerException => throw new OxygenException("Exception on Kafka Message ", n)
          case e: Throwable => throw new OxygenException("Exception on Kafka Message ", e)
        }
      }
    }
  }

  def sendMesage(topic: String, message: String, key: String): Unit = {
    if (oxygenConfig.oxygen.KAFKA_ENABLED) {
      try kafkaMessage.send(Option(topic), Option(message), Option(key))
      catch {
        case e: Throwable => throw new OxygenException("Exception on Kafka Message ", e)
      }
    }
  }

  // Send message for HF when we do not want to use the above batch method to minimize config conflict issue
  def sendMesageHf(topic: String, message: String, key: String): Unit = {
    try kafkaMessage.send(Option(topic), Option(message), Option(key))
    catch {
      case e: Throwable => {
        logger.debug("sendMessage on HF error " + e.getMessage)
        throw new OxygenException("Exception on Kafka Message ", e)
      }
    }
  }

  def sendMesageHf(topic: String, message: String, partition: Int, key: String): Unit = {
    try kafkaMessage.sendPartition(Option(topic), Option(message), Option(partition), Option(key))
    catch {
      case e: Throwable => {
        logger.debug("sendMessage on HF error " + e.getMessage)
        throw new OxygenException("Exception on Kafka Message ", e)
      }
    }
  }

  // This method doesn't appear to be working....
  def sendMesage(topic: String, message: String): Unit = {
    sendMesage(topic, message, null)
  }
}