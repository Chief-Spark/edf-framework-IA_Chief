package com.experian.edf.oxygen.validation.context

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.utils.JsonUtils
import com.experian.edf.oxygen.validation.domain.ValidationProcessor
import com.google.gson.JsonObject
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable

object ContextualValidator {

  @transient private lazy val logger = getLogger(this.getClass)

  private[validation] def validateContextually(
                                                contextPredicates: Map[String, Array[String]],
                                                validationProcessor: Broadcast[ValidationProcessor],
                                                pendingValidatedEvents: RDD[TaggedValidatedEvent],
                                                taggedCuratedEvents: RDD[TaggedCuratedEventWithMap],
                                                appContext: SparkOxygenAppContext
                                              ): RDD[TaggedValidatedEvent] = {
    if (contextPredicates.isEmpty) pendingValidatedEvents
    else {

      implicit val taggedCuratedEventEncoder = org.apache.spark.sql.Encoders.kryo[TaggedValidatedEvent]

      var newRDD: RDD[TaggedValidatedEvent] = pendingValidatedEvents;
      contextPredicates.toList.toStream.foreach(entry => {
        val contextName = entry._1
        val fields = entry._2

        val newEvents = validateForAContext(
          validationProcessor,
          contextName,
          fields,
          newRDD,
          taggedCuratedEvents
        )
        newRDD = appContext.sparkSession.emptyDataset[TaggedValidatedEvent].rdd
        newRDD = newEvents
      })

      newRDD
    }
  }

  private def validateForAContext(
                                   validationProcessor: Broadcast[ValidationProcessor],
                                   contextName: String,
                                   contextFields: Array[String],
                                   pendingValidatedEvents: RDD[TaggedValidatedEvent],
                                   taggedCuratedEvents: RDD[TaggedCuratedEventWithMap]
                                 ): RDD[TaggedValidatedEvent] = {

    val empty1 = mutable.ListBuffer[TaggedValidatedEvent]()
    val pendingValidatedEventsWithJoinKey = pendingValidatedEvents
      .map(x => {
        val joinKey = x.subscriber + ":" + contextFields.map(fieldName => {
          val f = JsonUtils.fromJson(x.validatedRecordJson, classOf[JsonObject]).get(fieldName)
          if (f != null) f.toString.replaceAll("^\"|\"$", "")
        }).mkString(":")
        (joinKey, x)
      })
      .aggregateByKey(empty1)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2)
      .map(x => {
        (x._1, x._2.toArray)
      })

    val empty2 = mutable.ListBuffer[TaggedCuratedEventWithMap]()
    val taggedCuratedEventsWithJoinKey = taggedCuratedEvents
      .map(x => {
        val joinKey = x.subscriber + ":" + contextFields.map(fieldName => x.curatedRecordJson.get(fieldName)).mkString(":")
        (joinKey, x)
      })
      .aggregateByKey(empty2)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2)
      .map(x => {
        (x._1, x._2.toArray)
      })

    val contextuallyValidatedEvents = taggedCuratedEventsWithJoinKey
      .rightOuterJoin(pendingValidatedEventsWithJoinKey)
      .map(x => {
        val taggedCuratedEvents = x._2._1.getOrElse(Array.empty)
        val pendingTaggedValidatedEvents = x._2._2

        val contextuallyValidatedEventsWithErrorsAndQS = validationProcessor.value.contextuallyValidate(pendingTaggedValidatedEvents,
          contextName, taggedCuratedEvents)

        contextuallyValidatedEventsWithErrorsAndQS.map(x => {
          val contextuallyValidatedEvent = x._1
          val qualityScores = x._2
          val errors = x._3
          val newTaggedValidatedEvent = new TaggedValidatedEvent(contextuallyValidatedEvent.lineage,
            contextuallyValidatedEvent.eventId, contextuallyValidatedEvent.validatedRecordJson,
            contextuallyValidatedEvent.payload, contextuallyValidatedEvent.qualityScores ++ qualityScores,
            contextuallyValidatedEvent.errorCodes ++ errors,
            contextuallyValidatedEvent.eventType, contextuallyValidatedEvent.minFrequency,
            contextuallyValidatedEvent.asOfDate, contextuallyValidatedEvent.didCreateAsOfDateFromReceivedDate,
            contextuallyValidatedEvent.subscriberRelation,
            contextuallyValidatedEvent.subscriber,
            contextuallyValidatedEvent.headerErrors,
            contextuallyValidatedEvent.footerErrors,
            contextuallyValidatedEvent.keys)
          newTaggedValidatedEvent
        })

      }).flatMap(x => x)

    contextuallyValidatedEvents.persist(StorageLevel.MEMORY_AND_DISK)
  }
}

