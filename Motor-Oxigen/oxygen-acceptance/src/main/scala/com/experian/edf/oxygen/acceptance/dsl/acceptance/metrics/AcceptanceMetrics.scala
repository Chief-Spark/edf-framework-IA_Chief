package com.experian.edf.oxygen.acceptance.dsl.acceptance.metrics

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.module.logging.OxygenEmitterFactory
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

object AcceptanceMetrics {
  @transient lazy val metricsEmitter = OxygenEmitterFactory.getMetricEmitter

  def emitStockMetric(name: String, key: String, value: Long, datasetId: String, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.ACCEPTANCE, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: Double, datasetId: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.ACCEPTANCE, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: String, datasetId: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.ACCEPTANCE, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

}
