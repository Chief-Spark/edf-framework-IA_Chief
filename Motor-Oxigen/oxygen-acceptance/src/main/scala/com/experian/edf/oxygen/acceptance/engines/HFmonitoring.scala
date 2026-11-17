package com.experian.edf.oxygen.acceptance.engines

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.module.logging.{OxygenEmitterFactory, OxygenMetricsEmitter}
import org.apache.commons.lang.exception.ExceptionUtils
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.StreamingQueryListener
import org.apache.spark.sql.streaming.StreamingQueryListener.{QueryProgressEvent, QueryStartedEvent, QueryTerminatedEvent}
import org.slf4j.LoggerFactory.getLogger

protected[oxygen] object HFmonitoring {
  @transient lazy val logger = getLogger(this.getClass)

  private val emitter: OxygenMetricsEmitter = OxygenEmitterFactory.getMetricEmitter

  def hfStreamListener(appContext: SparkOxygenAppContext, stage: String, spark: SparkSession) =
    spark.streams.addListener(new StreamingQueryListener {
      override def onQueryStarted(event: QueryStartedEvent): Unit = logger.debug(s"Streaming Query started: (ID: ${event.id}, Name=${event.name})")

      override def onQueryProgress(event: QueryProgressEvent): Unit = {
        event.progress.sources.foreach(each => {

          try {
            emitter
              .emitImportant(MetricEventsFactory
                .datasetKVDataMetrics
                .hfMetricLong(stage + "_" + appContext.sc.applicationId,
                  MetricsConstants.Keys.TOTAL_RECORDS,
                  stage,
                  appContext.sc.applicationId,
                  MetricsConstants.Keys.TOTAL_RECORDS,
                  each.numInputRows,
                  MetricsConstants.MetricType.PROCESS
                ))
          } catch {
            case e: Exception =>
              logger.error("Exception while sending streaming progress : " + e.getMessage)
              logger.error(ExceptionUtils.getFullStackTrace(e))
          }
        })
      }

      override def onQueryTerminated(event: QueryTerminatedEvent): Unit = logger.debug(s"Streaming Query Terminated: (ID: ${event.id}, Exception=${event.exception.orNull})")
    })

}
