package com.experian.edf.oxygen.validation.context

import java.io.File
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.SubscriberSubscriberRelation
import com.experian.edf.oxygen.io.OxygenIO
import com.experian.edf.oxygen.validation.domain.ValidationProcessor
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory


// TODO: All the reader methods/class should move to IO Utils.

private[validation] trait SSREventReader {
  def readSSREvent(appContext: SparkOxygenAppContext): RDD[SubscriberSubscriberRelation]
}

private[validation] object SSREventReaderImpl extends SSREventReader {
  @transient private lazy val logger = LoggerFactory.getLogger(this.getClass)

  override def readSSREvent(appContext: SparkOxygenAppContext): RDD[SubscriberSubscriberRelation] = {
    val curationConfig = appContext.config.oxygen.stage.curation.get
    val ssrDir = curationConfig.paths.curatedSSRDir + File.separator + "*"
    val oxygenIO = new OxygenIO(appContext.sparkSession)
    import appContext.sparkSession.implicits._
    val recs = try {
      oxygenIO.read.dataframe(ssrDir, Constants.FILE_PARQUET_FORMAT).distinct().as[SubscriberSubscriberRelation].rdd
    } catch {
      case exception: Exception => {
        logger.warn(s"Unable to read Curated SSRs. Proceeding assuming there are no Curated SSRs - Exception message : ${exception.getMessage}")
        logger.debug("Unable to read Curated SSRs", exception)
        appContext.sc.emptyRDD[SubscriberSubscriberRelation]
      }
    }
    recs
  }
}

class ContextuallyValidateSubscriberRelation(appContext: SparkOxygenAppContext,
                                             ssrReader: SSREventReader = SSREventReaderImpl) {

  def performSSRValidation(pendingValidatedEvents: RDD[TaggedValidatedEvent],
                           processor: Broadcast[ValidationProcessor]): RDD[TaggedValidatedEvent] = {
    val ssrEvents = ssrReader.readSSREvent(appContext)

    val ssrEventsWithKey = ssrEvents.map(x => (x.subscriber + ":" + x.subscriberRelation, 1))
    //println(ssrEventsWithKey.count)
    val pendingValidatedEventsWithKey = pendingValidatedEvents.map(x => (x.subscriber + ":" + x.subscriberRelation, x))
    val newTaggedValidatedEvents = pendingValidatedEventsWithKey.leftOuterJoin(ssrEventsWithKey).map(x => {
      val key = x._1
      val pendingValidatedEvent = x._2._1
      val isNewSSR = if (x._2._2.getOrElse(0) == 0) true else false

      if (isNewSSR) {
        val qsAndErrors = processor.value.validateNewSubscriberRelation(pendingValidatedEvent).orNull

        if (qsAndErrors != null) {
          val qualityScores = qsAndErrors._1
          val errors = qsAndErrors._2
          new TaggedValidatedEvent(pendingValidatedEvent.lineage,
            pendingValidatedEvent.eventId, pendingValidatedEvent.validatedRecordJson,
            pendingValidatedEvent.payload, pendingValidatedEvent.qualityScores ++ qualityScores,
            pendingValidatedEvent.errorCodes ++ errors,
            pendingValidatedEvent.eventType, pendingValidatedEvent.minFrequency,
            pendingValidatedEvent.asOfDate, pendingValidatedEvent.didCreateAsOfDateFromReceivedDate,
            pendingValidatedEvent.subscriberRelation,
            pendingValidatedEvent.subscriber,
            pendingValidatedEvent.headerErrors,
            pendingValidatedEvent.footerErrors,
            pendingValidatedEvent.keys)
        } else
          pendingValidatedEvent

      } else
        pendingValidatedEvent
    })
    newTaggedValidatedEvents
  }
}
