package com.experian.edf.oxygen.acceptance.engines.hf


import java.util.concurrent.ConcurrentHashMap
import com.experian.edf.oxygen.acceptance.engines.KafkaMsg
import com.experian.edf.oxygen.domain.internal.{NotificationContext, NotificationLoad, Notifications, TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.utils.{JsonUtils, ScalaJacksonUtil}
import com.google.gson.Gson
import org.slf4j.LoggerFactory.getLogger



object HfNotifications extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  def sendNotification(kafkaMsg: KafkaMsg, load: NotificationLoad, stage: String, status: String) = {
    try {
      if (load != null && load.notificationContext != null && load.notificationContext.channel != "") {
        kafkaMsg.sendMesageHf(load.notificationContext.channel, new Gson().toJson(Notifications(load, stage, status)), key = "")
        true
      }
      else true
    }
    catch {
      case ex: Exception =>
        ex.printStackTrace()
        logger.warn("Error on Hf sending notification " + ex.getMessage)
        true
    }
  }

  def sendFromAcceptance(cache: ConcurrentHashMap[String, Boolean], kafkaMsg: KafkaMsg, load: NotificationLoad, stage: String, status: String) = {
    if (cache.get(load.notificationId) == true) None
    else {
      sendNotification(kafkaMsg, load, stage, status)
      cache.put(load.notificationId, true)
    }
  }

  def getNotificationOnErrorHandleTcem(value: String): NotificationLoad = {
    val notificationLoad = try {
      val tce = JsonUtils.fromJson(value, classOf[TaggedCuratedEventWithMap])
      val notificationLoad = tce.notificationLoad
      notificationLoad
    } catch {
      case ex: Exception => {
        logger.error("Error on getting HF State Message " + ex.getMessage)
        NotificationLoad(NotificationContext("", "", ""), "", "")
      }
    }
    notificationLoad
  }

  def getNotificationOnErrorHandleTve(value: String): NotificationLoad = {
    val notificationLoad = try {
      val tce = JsonUtils.fromJson(value, classOf[TaggedValidatedEvent])
      val notificationLoad = tce.notificationLoad
      notificationLoad
    } catch {
      case ex: Exception => {
        logger.error("Error on getting HF State Message " + ex.getMessage)
        NotificationLoad(NotificationContext("", "", ""), "", "")
      }
    }
    notificationLoad
  }

  def getNotificationOnErrorHandleTcem(tce: TaggedCuratedEventWithMap): NotificationLoad = {
    val notificationLoad = if (tce != null) tce.notificationLoad
    else NotificationLoad(NotificationContext("", "", ""), "", "")

    notificationLoad
  }

  def getNotificationOnErrorHandleTce(tce: TaggedCuratedEvent): NotificationLoad = {
    val notificationLoad = if (tce != null && tce.notificationLoad != null) tce.notificationLoad
    else NotificationLoad(NotificationContext("", "", ""), "", "")

    notificationLoad
  }

  def getNotificationLoadOnErrorHandleMap(value: String) = {
    try {
      val message = ScalaJacksonUtil.fromJson(value, classOf[Map[String, Object]]).get
      val notificationld = getNotificationLoad(message, value)
      notificationld
    } catch {
      case ex: Exception => {
        ex.printStackTrace()
        logger.error("Error on HF Message " + ex.getMessage)
        NotificationLoad(NotificationContext("", "", ""), "", "")
      }
    }
  }

  def getNotificationLoad(metadata: Map[String, Object], originalMessage: String): NotificationLoad = {
    val source = metadata.get("source").getOrElse("").toString
    val channel = metadata.get("channel").getOrElse("").toString
    val messageId = metadata.get("messageId").getOrElse("").toString
    val uuid = java.util.UUID.randomUUID().toString

    val notificationLoad = if (channel != "") {
      val noctxt = NotificationContext(source, channel, messageId)
      NotificationLoad(noctxt, originalMessage, uuid)
    } else NotificationLoad(NotificationContext("", "", ""), "", "")


    notificationLoad
  }
}
