package com.experian.edf.oxygen.curation.engines.hf

import com.experian.edf.oxygen.curation.domain.HFErrorEvent
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.LoggerFactory.getLogger

case class ErrorMsgWStackTrace(inputMsg: String, inputTopic: String, error: String, stackTrace: String)

case class HfError(stage: String, errorMsg: ErrorMsgWStackTrace)

private[oxygen] object HfMessage {
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def sendErrorStackTrace(kafkaMsg: KafkaMsg,
                                          stageName: String,
                                          hfErrorEvent: HFErrorEvent,
                                          errorTopic: String) = {
    logger.debug(s"error happended + ${hfErrorEvent.error}")

    val errorMsgStack = ErrorMsgWStackTrace(hfErrorEvent.value, hfErrorEvent.inputTopic, hfErrorEvent.error, hfErrorEvent.stackTrace)

    val stageErrorStackTrace = HfError(stageName, errorMsgStack)
    val stageErrorJsonStr = ScalaJacksonUtil.toJson(stageErrorStackTrace).getOrElse("")
    kafkaMsg.sendMesageHf(errorTopic, stageErrorJsonStr, "")
  }

  private[oxygen] def sendErrorStackTrace(kafkaMsg: KafkaMsg,

                                          inputTopic: String,
                                          stageName: String,
                                          originalMsg: String,
                                          exceptionMsg: String,
                                          stackTraceMsg: String,
                                          errorTopic: String) = {
    logger.debug(s"error happended + ${exceptionMsg}")

    val errorMsgStack = ErrorMsgWStackTrace(originalMsg, inputTopic, exceptionMsg, stackTraceMsg)

    val stageErrorStackTrace = HfError(stageName, errorMsgStack)
    val stageErrorJsonStr = ScalaJacksonUtil.toJson(stageErrorStackTrace).getOrElse("")
    kafkaMsg.sendMesageHf(errorTopic, stageErrorJsonStr, "")
  }
}
