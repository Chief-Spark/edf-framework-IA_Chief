package com.experian.edf.oxygen.validation.engines

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.logging.MetricEvent
import com.experian.edf.oxygen.validation.log.{ValidationFieldMetricEvent, ValidationMetricName}
import org.apache.spark.sql.functions.{count, lit}
import org.apache.spark.sql.Dataset

private case class MetricEventCount(metricSubject: String,
                                    metricKey: String,
                                    metricValue: Long,
                                    override val metricData: String = null) extends MetricEvent


private object ValidationMetricLoggingDictionary {
  val metricSubject = "metricSubject"
  val metricKey = "metricKey"
  val metricValue = "metricValue"
  val metricData = "metricData"
}


object ValidationMetricCalculator extends Serializable {

  private[validation] def calculateCountOfErrorByFieldAndCode(appContext: SparkOxygenAppContext,
                                                              dataSetId: String,
                                                              taggedValidatedEvents: Dataset[TaggedValidatedEvent]): ValidationFieldMetricEvent = {
    import appContext.sparkSession.implicits._
    val countOfErrorsByFieldAndCode = taggedValidatedEvents.flatMap(x => {
      x.errorCodes.map(y => (y.errorCodeName, y.errorCodeValue))
    })
      .toDF(ValidationMetricLoggingDictionary.metricSubject, ValidationMetricLoggingDictionary.metricKey)
      .groupBy(ValidationMetricLoggingDictionary.metricSubject, ValidationMetricLoggingDictionary.metricKey)
      .agg(count("*").as(ValidationMetricLoggingDictionary.metricValue))
      .withColumn(ValidationMetricLoggingDictionary.metricData, lit(null))
      .as[MetricEventCount]
      .collect()

    new ValidationFieldMetricEvent(dataSetId, ValidationMetricName.errorCountByField, countOfErrorsByFieldAndCode.toList, true)
  }

  private[validation] def calculateCountOfErrorByFieldAndLevel(appContext: SparkOxygenAppContext,
                                                               dataSetId: String,
                                                               taggedValidatedEvents: Dataset[TaggedValidatedEvent]): Option[ValidationFieldMetricEvent] = {
    import appContext.sparkSession.implicits._
    val errorCodesWithLevel = taggedValidatedEvents.flatMap(x => {
      x.errorCodes.map(y => (y.errorCodeName, y.errorLevel))
    })
      .filter(x => x._2 != null && x._2.nonEmpty).cache

    if (errorCodesWithLevel.count != 0) {
      val countOfErrorsByFieldAndLevel = errorCodesWithLevel
        .toDF(ValidationMetricLoggingDictionary.metricSubject, ValidationMetricLoggingDictionary.metricKey)
        .groupBy(ValidationMetricLoggingDictionary.metricSubject, ValidationMetricLoggingDictionary.metricKey)
        .agg(count("*").as(ValidationMetricLoggingDictionary.metricValue))
        .withColumn(ValidationMetricLoggingDictionary.metricData, lit(null))
        .as[MetricEventCount]
        .collect()

      errorCodesWithLevel.unpersist(false)

      Some(new ValidationFieldMetricEvent(dataSetId,
        ValidationMetricName.errorCountByCodeAndLevel,
        countOfErrorsByFieldAndLevel.toList,
        true))
    } else {
      errorCodesWithLevel.unpersist(false)
      None
    }
  }

  private[validation] def calculateCountOfErrorByCode(appContext: SparkOxygenAppContext,
                                                      dataSetId: String,
                                                      taggedValidatedEvents: Dataset[TaggedValidatedEvent]): ValidationFieldMetricEvent = {
    import appContext.sparkSession.implicits._
    val countOfErrorByCode = taggedValidatedEvents.flatMap(x => {
      x.errorCodes.map(y => y.errorCodeValue)
    })
      .toDF(ValidationMetricLoggingDictionary.metricKey)
      .groupBy(ValidationMetricLoggingDictionary.metricKey)
      .agg(count("*").as(ValidationMetricLoggingDictionary.metricValue))
      .withColumn(ValidationMetricLoggingDictionary.metricData, lit(null))
      .withColumn(ValidationMetricLoggingDictionary.metricSubject, lit(null))
      .as[MetricEventCount]
      .collect()

    new ValidationFieldMetricEvent(dataSetId, ValidationMetricName.errorCountByCode, countOfErrorByCode.toList, true)
  }

  private[validation] def calculateCountOfErrorByLevel(appContext: SparkOxygenAppContext,
                                                       dataSetId: String,
                                                       taggedValidatedEvents: Dataset[TaggedValidatedEvent]): Option[ValidationFieldMetricEvent] = {
    import appContext.sparkSession.implicits._
    val errorCountWithLevel = taggedValidatedEvents.flatMap(x => {
      x.errorCodes.map(y => y.errorLevel)
    })
      .filter(x => x != null && x.nonEmpty).cache

    if (errorCountWithLevel.count != 0) {
      val countOfErrorsByFieldAndLevel = errorCountWithLevel
        .toDF(ValidationMetricLoggingDictionary.metricKey)
        .groupBy(ValidationMetricLoggingDictionary.metricKey)
        .agg(count("*").as(ValidationMetricLoggingDictionary.metricValue))
        .withColumn(ValidationMetricLoggingDictionary.metricData, lit(null))
        .withColumn(ValidationMetricLoggingDictionary.metricSubject, lit(null))
        .as[MetricEventCount]
        .collect()

      errorCountWithLevel.unpersist(false)

      Some(new ValidationFieldMetricEvent(dataSetId,
        ValidationMetricName.errorCountByLevel,
        countOfErrorsByFieldAndLevel.toList,
        true))
    } else {
      errorCountWithLevel.unpersist(false)
      None
    }
  }

}

