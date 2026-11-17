package com.experian.edf.oxygen.validation.log

import com.experian.edf.oxygen.domain.internal.FileMetadata
import com.experian.edf.oxygen.global.constants.logging.LegacyOxygenLoggingConstants
import com.experian.edf.oxygen.logging._
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import com.experian.edf.oxygen.validator.internal.ObjectError

trait ValidationStageEvent extends StageEvent {
  override val stage: String = LegacyOxygenLoggingConstants.stages.VALIDATION
}

private[oxygen] object ValidationMetricName {
  val filename = "header"
  val header = "header"
  val footer = "footer"
  val historicAttributes = "historicattributes"
  val fileMetrics = "filemetrics"
  val datasetmetrics = "datasetmetrics"
  val totalRecords = "totalrecords"
  val errorCountByField = "errorCountByField"
  val errorCountByCode = "errorCountByCode"
  val errorCountByCodeAndLevel = "errorCountByCodeAndLevel"
  val errorCountByLevel = "errorCountByLevel"
  val datasetCustomMetrics = "datasetCustomMetrics"


}

case class FileMetadataLoggingEvent(datasetId: String, datasetMetadata: FileMetadata) extends DatasetProcessInfoLoggingEvent with ValidationStageEvent

class FileProcessorLoggingEvent(val datasetId: String, val status: String) extends DatasetProcessInfoLoggingEvent with ValidationStageEvent

class FilenameValidationEvent(datasetIdentifier: String, val status: String, objectErrors: Array[ObjectError], data: Any)
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.filename, objectErrors, data)

class HeaderValidationEvent(datasetIdentifier: String, val status: String, objectErrors: Array[ObjectError], data: Any)
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.header, objectErrors, data)

class FooterValidationEvent(datasetIdentifier: String, val status: String, objectErrors: Array[ObjectError], data: Any)
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.footer, objectErrors, data)

class HistoricAttributesValidationEvent(datasetIdentifier: String, val status: String, objectErrors: Array[ObjectError], data: Any)
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.historicAttributes, objectErrors, data)

class ValidationMetricsLoggingEvent(datasetIdentifier: String, metrics: Array[MetricNameExprEvent])
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.fileMetrics, metrics) with SummaryEvent

class ValidationDatasetMetricsLoggingEvent(datasetIdentifier: String, metrics: Map[String, Any])
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.datasetmetrics, metrics) with SummaryEvent

class TotalRecordsMetrics(datasetIdentifier: String, totalRecords: Long)
  extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.VALIDATION, datasetIdentifier, ValidationMetricName.totalRecords, totalRecords) with SummaryEvent


case class MetricNameExprEvent(metricId: String, metricExpression: String, metricValue: Any)

class ValidationFieldMetricEvent(datasetIdentifier: String, metricIdentifier: String, value: List[MetricEvent], summary: Boolean = true)
  extends MetricsLoggingEvent with ValidationStageEvent with SummaryEvent {
  override val metricName: String = metricIdentifier
  override val metrics = value
  override val datasetId: String = datasetIdentifier
  override val isDatasetSummary = summary

  override def toString = ScalaJacksonUtil.toJson(this).get
}


// Flattened Metric Class.  Dataset operation does not support inheritance.
final case class ValidationMetricEventForDatasetOpe(eventType: String = LegacyOxygenLoggingConstants.metricsEventType,
                                                    metricName: String,
                                                    datasetId: String,
                                                    timestamp: Long,
                                                    stage: String = LegacyOxygenLoggingConstants.stages.VALIDATION,
                                                    metric: Array[MetricEvent])
