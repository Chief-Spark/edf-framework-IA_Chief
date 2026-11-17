package com.experian.edf.oxygen.acceptance.base

import com.experian.edf.oxygen.base.{Constants}

import java.time.Instant
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.global.processmngt.constants.PmConstants
import com.experian.edf.oxygen.global.utils.ResourceUtils
import com.experian.edf.oxygen.logging.events.stage.ServiceUpdates
import com.experian.edf.oxygen.module.logging.OxygenEmitterFactory
import com.experian.edf.oxygen.logging.events.stage.HTServiceUpdates
import com.experian.edf.oxygen.orchestration.{OrchestrationContext, OrchestrationHelper, OxygenOrchestrator}
import org.slf4j.LoggerFactory

import java.time.Instant

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2019-07-08 at 08:36
 */
private[oxygen] abstract class OxygenOrchestratedService(appContext: OxygenAppContext) extends OxygenService(appContext) {

  @transient private lazy val log = LoggerFactory.getLogger(classOf[OxygenOrchestratedService])
  @transient lazy val metricsEmitter = OxygenEmitterFactory.getMetricEmitter

  // This is the process manager which will manage the handling of files via Kafka / DB / Filesystem

  val serviceUpdatesEnabled = oxygenConf.oxygen.isFeatureActive(Constants.features.ENABLE_SERVICE_UPDATES)
  val operationsUpdateTopic: Option[String] = oxygenConf.oxygen.getKafkaLoggerTopic.get(Constants.loggerTopics.SERVICE_UPDATE)

  val orchestrationContext: OrchestrationContext = createOrchestrationContext
  @transient val orchestrator: OxygenOrchestrator = OchestrationFactory.create(orchestrationContext, this)
  val orchestrationHelper = new OrchestrationHelper(orchestrator, orchestrationContext.action, orchestrationContext.source, orchestrationContext.applicationId.orNull, None)

  def createOrchestrationContext: OrchestrationContext

  private val serviceInfo = new HTServiceUpdates()
  serviceInfo.setApplicationId(
      appContext match {
        case context: SparkOxygenAppContext => context.sc.applicationId
        case _ => null
      }
    )

    serviceInfo.setGeneration(appContext.config.oxygen.generation.candidate.orNull)
    serviceInfo.setComments(orchestrationContext.action + " started")
    serviceInfo.setProcessId(ResourceUtils.getProcessID().toString)
    serviceInfo.setRunId(orchestrationContext.runId)
    serviceInfo.setDataPlatform(oxygenConf.oxygen.DATA_PLATFORM)
    serviceInfo.setEntity(oxygenConf.oxygen.ENTITY)
    serviceInfo.setEnvironment(oxygenConf.oxygen.ENVIRONMENT)
    serviceInfo.setMetricSource(orchestrationContext.action)
    serviceInfo.setStartTimestamp(Instant.now().toEpochMilli)
    serviceInfo.setStatus(PmConstants.status.IN_PROGRESS)

  def publishServiceUpdates(): Unit = {
    val message = serviceInfo.log
    log.debug(message)
    if (serviceUpdatesEnabled)
      messaging.send(operationsUpdateTopic.get, message, serviceInfo.getRunId)
  }

  def startService(additionalInfo: Map[String, Any] = Map.empty): Unit = {
    val map: Map[String, AnyRef] = additionalInfo.map(each => (each._1, each._2.asInstanceOf[AnyRef]))
    orchestrationHelper.htStageStart(map)
    publishServiceUpdates()
  }

  def addServiceInfoMetric(key: String, value: Any): Unit = {
    serviceInfo.getMetrics.put(key, value.asInstanceOf[AnyRef])
  }

  def addServiceInfoPaths(key: String, value: Any): Unit = serviceInfo.getPaths.put(key, value.asInstanceOf[AnyRef])

  def endService(additionalInfo: Map[String, Any] = Map.empty): Unit = {
    val map: Map[String, AnyRef] = additionalInfo.map(each => (each._1, each._2.asInstanceOf[AnyRef]))
    orchestrationHelper.htStageEnd(map)
    serviceInfo.setEndTimestamp(Instant.now().toEpochMilli)
    serviceInfo.setStatus(PmConstants.status.SUCCESS)
    serviceInfo.setComments(orchestrationContext.action + " completed")
    publishServiceUpdates()
  }
}
