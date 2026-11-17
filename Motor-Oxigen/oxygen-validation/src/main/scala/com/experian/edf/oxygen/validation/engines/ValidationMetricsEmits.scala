package com.experian.edf.oxygen.validation.engines

import com.experian.edf.oxygen.domain.FileMetadata
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.module.logging.metrics.MetricsConstants.MetricName.DATASET_CUSTOM_METRICS
import com.experian.edf.oxygen.module.logging.metrics.MetricsConstants.Keys.METRIC_EXPRESSION
import com.experian.edf.oxygen.module.logging.{OxygenEmitterFactory, OxygenMetricsEmitter}
import com.experian.edf.oxygen.validation.log.{MetricNameExprEvent, ValidationFieldMetricEvent}
import org.slf4j.LoggerFactory.getLogger

import scala.util.{Failure, Success, Try}

object ValidationMetricsEmits {

  @transient private lazy val logger = getLogger(this.getClass)

  private val COUNT_BY_LEVEL = "countByLevel"
  private val COUNT_BY_CODE = "countByCode"
  private val ERROR_COUNT_FIELD_AND_CODE = "errorCountByFieldAndCode"
  private val ERROR_COUNT_FIELD_AND_LEVEL = "errorCountByFieldAndLevel"
  private val ERROR_FIELD = "errorField"

  private val emitter: OxygenMetricsEmitter = OxygenEmitterFactory.getMetricEmitter

  def emitHTFileMetadata(datasetId: String, fileMetadata: FileMetadata): Unit = {
    val totalRecordsEvent = MetricEventsFactory.datasetDataMetrics.htMetric("datasetMetadata", MetricsConstants.Actions.VALIDATION, datasetId, Map("datasetMetadata" -> fileMetadata))
    emitter.emitImportant(totalRecordsEvent)
  }

  def emitHTTotalRecords(datasetId: String, totalRecords: Long): Unit = {
    val totalRecordsEvent = MetricEventsFactory.datasetKVDataMetrics.htMetricLong(MetricsConstants.MetricName.DATASET_INFO, MetricsConstants.Actions.VALIDATION, datasetId, MetricsConstants.Keys.TOTAL_RECORDS, totalRecords)
    emitter.emitImportant(totalRecordsEvent)
  }

  def emitHTErrorCountByFieldAndLevel(event: Option[ValidationFieldMetricEvent]) = {
    if (event.isDefined)
      event.get.metrics.foreach(each => {
        Try(each.metricValue.toString.toLong) match {
          case Success(value) =>
            emitter.emitImportant(
              MetricEventsFactory.datasetKVDataMetrics.htMetricLong(
                ERROR_COUNT_FIELD_AND_LEVEL,
                MetricsConstants.Actions.VALIDATION,
                event.get.datasetId,
                each.metricKey,
                value,
                OxygenCollectionsUtils.asJavaMapObject(Map(ERROR_FIELD -> each.metricSubject))
              )
            )
          case Failure(exception) => logger.debug(s"Error while converting CountByCode to long : ${exception.getMessage}", exception)
        }
      })
  }

  def emitHTErrorCountByFieldAndCode(event: Option[ValidationFieldMetricEvent]): Unit = {
    if (event.isDefined)
      event.get.metrics.foreach(each => {
        Try(each.metricValue.toString.toLong) match {
          case Success(value) =>
            emitter.emitImportant(
              MetricEventsFactory.datasetKVDataMetrics.htMetricLong(
                ERROR_COUNT_FIELD_AND_CODE,
                MetricsConstants.Actions.VALIDATION,
                event.get.datasetId,
                each.metricKey,
                value,
                OxygenCollectionsUtils.asJavaMapObject(Map(ERROR_FIELD -> each.metricSubject))
              )
            )
          case Failure(exception) => logger.debug(s"Error while converting CountByCode to long : ${exception.getMessage}", exception)
        }
      })
  }

  def emitHTCountByCode(event: Option[ValidationFieldMetricEvent]): Unit = {
    if (event.isDefined)
      event.get.metrics.foreach(each => {
        Try(each.metricValue.toString.toLong) match {
          case Success(value) => emitter.emitImportant(MetricEventsFactory.datasetKVDataMetrics.htMetricLong(COUNT_BY_CODE, MetricsConstants.Actions.VALIDATION, event.get.datasetId, each.metricKey, value))
          case Failure(exception) => logger.debug(s"Error while converting CountByCode to long : ${exception.getMessage}", exception)
        }
      })
  }

  def emitHTCountByLevel(event: Option[ValidationFieldMetricEvent]): Unit = {
    if (event.isDefined)
      event.get.metrics.foreach(each => {
        Try(each.metricValue.toString.toLong) match {
          case Success(value) => emitter.emitImportant(MetricEventsFactory.datasetKVDataMetrics.htMetricLong(COUNT_BY_LEVEL, MetricsConstants.Actions.VALIDATION, event.get.datasetId, each.metricKey, value))
          case Failure(exception) => logger.debug(s"Error while converting ErrorCountByLevel to long : ${exception.getMessage}", exception)
        }
      })
  }

  def emitHTCustomDatasetMetrics(datasetId: String, metricsEvents: List[MetricNameExprEvent]): Any = {
    metricsEvents.foreach(each => {
      val valueAsString = each.metricValue.toString
      if (Try(valueAsString.toLong).isSuccess) {
        emitter.emitImportant(MetricEventsFactory.datasetKVDataMetrics.htMetricLong(
          DATASET_CUSTOM_METRICS,
          MetricsConstants.Actions.VALIDATION,
          datasetId,
          each.metricId,
          valueAsString.toLong,
          OxygenCollectionsUtils.asJavaMapObject(Map(METRIC_EXPRESSION -> each.metricExpression)))
        )
      }
      else if (Try(valueAsString.toDouble).isSuccess) {
        emitter.emitImportant(MetricEventsFactory.datasetKVDataMetrics.htMetricDouble(
          DATASET_CUSTOM_METRICS,
          MetricsConstants.Actions.VALIDATION,
          datasetId,
          each.metricId,
          valueAsString.toDouble,
          OxygenCollectionsUtils.asJavaMapObject(Map(METRIC_EXPRESSION -> each.metricExpression)))
        )
      }
      else {
        emitter.emitImportant(MetricEventsFactory.datasetKVDataMetrics.htMetricString(
          DATASET_CUSTOM_METRICS,
          MetricsConstants.Actions.VALIDATION,
          datasetId,
          each.metricId,
          valueAsString,
          OxygenCollectionsUtils.asJavaMapObject(Map(METRIC_EXPRESSION -> each.metricExpression)))
        )
      }
    })
  }

}
