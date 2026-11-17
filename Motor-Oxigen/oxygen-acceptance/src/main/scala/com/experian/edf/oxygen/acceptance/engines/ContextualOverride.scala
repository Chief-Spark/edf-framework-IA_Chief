package com.experian.edf.oxygen.acceptance

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.internal.{InferredAsDeleted, TaggedCuratedEvent, TaggedValidatedEvent}
import com.experian.edf.oxygen.utils.JsonUtils
import org.apache.spark.TaskContext
import com.experian.edf.oxygen.acceptance.util.IdGenerator
import com.experian.edf.oxygen.domain.{Lineage, Payload, Period, QualityScore}
import com.experian.edf.oxygen.validator.ObjectError
import com.google.gson.{JsonElement, JsonObject}
import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory.getLogger


class ContextualOverride(appContext: SparkOxygenAppContext, currentPeriod: Period, subscriber: String) extends Serializable {

  @transient lazy val logger = getLogger(this.getClass)

  def inferDelete(contextPeriodCount: Int,
                  contextKey: Array[String],
                  tve: RDD[TaggedValidatedEvent],
                  fileName: String,
                  curatedEvents: RDD[TaggedCuratedEvent]): RDD[TaggedValidatedEvent] = {

    val contextualTaggedCuratedEvents = curatedEventForContextPeriod(contextPeriodCount,
      curatedEvents.filter(x => x.lineage.subscriber == subscriber)).cache

    if (!contextualTaggedCuratedEvents.isEmpty)
      inferEventsToBeDeleted(contextKey, tve, fileName, contextualTaggedCuratedEvents)
    else
      appContext.sc.emptyRDD[TaggedValidatedEvent]
  }

  private def inferEventsToBeDeleted(
                                      contextKey: Array[String],
                                      tve: RDD[TaggedValidatedEvent],
                                      fileName: String,
                                      taggedCuratedEvents: RDD[TaggedCuratedEvent]): RDD[TaggedValidatedEvent] = {

    val pendingValidatedEventsWithJoinKey = tve
      .map(x => {
        val joinKey = x.subscriber + ":" + contextKey.map(fieldName => {
          TaggedValidatedEvent.getValueForField(fieldName, x).getOrElse("")
        }).mkString(":")
        (joinKey, x)
      })

    val taggedCuratedEventsWithJoinKey = taggedCuratedEvents.map(x => {
      val joinKey = x.subscriber + ":"  + contextKey.map(fieldName => {
        TaggedCuratedEvent.getValueForField(fieldName, x).getOrElse("")
      }).mkString(":")
      (joinKey, x)
    })

    val eventsToBeDeleted = taggedCuratedEventsWithJoinKey
      .leftOuterJoin(pendingValidatedEventsWithJoinKey)
      .filter(x => {
        x._2._2.isEmpty
      })
      .map(x => {
        val joinKeysInJson = jsonStringForKeys(x._1, contextKey)

        val tce = x._2._1
        val lineage = new Lineage(dataSource = "Oxygen",
          receivedDate = currentPeriod.epochDateTime,
          fileName,
          subscriber =  tce.lineage.subscriber,
          fileAsOfDate = currentPeriod.epochDateTime)
        val inferredAsDeleted = new InferredAsDeleted("{" + joinKeysInJson + "}", true)

        val deletedTaggedValidatedEvent = new TaggedValidatedEvent(lineage,
          IdGenerator.nextEventId(appContext.config.oxygen.JOB_ID, TaskContext.getPartitionId()),
          "",
          Payload(Map.empty), Array.empty[QualityScore],
          Array.empty[ObjectError],
          tce.eventType, tce.minFrequency,
          tce.asOfDate, tce.didCreateAsOfDateFromReceivedDate,
          tce.subscriberRelation,
          tce.subscriber,
          Array.empty[ObjectError],
          Array.empty[ObjectError],
          "",
          inferredAsDeleted,
          tce.fileTag,
          tce.notificationLoad)
        deletedTaggedValidatedEvent
      })
    eventsToBeDeleted
  }

  private def curatedEventForContextPeriod(contextPeriodCount: Int,
                                           taggedCuratedEvents: RDD[TaggedCuratedEvent]): RDD[TaggedCuratedEvent] = {
    if (contextPeriodCount < 0)
      taggedCuratedEvents
    else
      taggedCuratedEvents
        .filter(x => {
          x.asOfPeriod > currentPeriod.minus(contextPeriodCount)
        })
  }

  private def jsonStringForKeys(joinKey: String, contextKey: Array[String]): String = {
    val keys = joinKey.split(":")
    assert(keys.length == contextKey.length + 1)
    (for (i <- 1 to keys.length-1) yield ("\"" + contextKey(i-1) + "\"") + ":" + keys(i)).mkString(",")
  }

  def explicitlyDelete(contextPeriodCount: Int,
                       contextKey: Array[String],
                       tve: RDD[TaggedValidatedEvent],
                       fileName: String,
                       curatedEvents: RDD[TaggedCuratedEvent]): RDD[TaggedValidatedEvent] = {
    val contextualTaggedCuratedEvents = curatedEventForContextPeriod(contextPeriodCount,
      curatedEvents.filter(x => x.lineage.subscriber == subscriber)).cache

    if (!contextualTaggedCuratedEvents.isEmpty)
      generateEventsToBeDeleted(contextKey, tve, fileName, contextualTaggedCuratedEvents)
    else
      appContext.sc.emptyRDD[TaggedValidatedEvent]

  }

  private def generateEventsToBeDeleted(contextKey: Array[String],
                                        tve: RDD[TaggedValidatedEvent],
                                        fileName: String,
                                        taggedCuratedEvents: RDD[TaggedCuratedEvent]): RDD[TaggedValidatedEvent] = {
    val deleteDirectiveEvents = tve
      .filter(x => {
        val deleteDirective = TaggedValidatedEvent.isDeleteDirective(x)
        if (!deleteDirective) false
        else {
          // filter the delete directive events, which has the context keys
          contextKey.map(k => {
            val fv = try {
              JsonUtils.fromJson(x.validatedRecordJson, classOf[JsonObject]).get(k)
            } catch {
              case ex: Exception =>  null
            }
            if (fv == null) false
            else true
          }).reduce(_ && _)
        }
      })
    if (deleteDirectiveEvents.isEmpty)
      appContext.sc.emptyRDD[TaggedValidatedEvent]
    else {
      val deleteDirectiveEventsWithJoinKey = deleteDirectiveEvents
        .map(x => {
          val joinKey = x.subscriber + ":" + contextKey.map(fieldName => {
            TaggedValidatedEvent.getValueForField(fieldName, x).getOrElse("")
          }).mkString(":")
          (joinKey, x)
        })

      val taggedCuratedEventsWithJoinKey = taggedCuratedEvents.map(x => {
        val joinKey = x.subscriber + ":" + contextKey.map(fieldName => {
          TaggedCuratedEvent.getValueForField(fieldName, x).getOrElse("")
        }).mkString(":")
        (joinKey, x)
      })

      val eventsToBeDeleted = taggedCuratedEventsWithJoinKey
        .join(deleteDirectiveEventsWithJoinKey)
        .filter(x => {
          val tce = x._2._1
          val tve = x._2._2
          tce.asOfDate <= tve.asOfDate
        })
        .map(x => {
          val joinKeysInJson = jsonStringForKeys(x._1, contextKey)

          val tce = x._2._1
          val lineage = new Lineage(dataSource = "Oxygen",
            receivedDate = currentPeriod.epochDateTime,
            fileName,
            subscriber = tce.lineage.subscriber,
            fileAsOfDate = currentPeriod.epochDateTime)
          val inferredAsDeleted = new InferredAsDeleted("{" + joinKeysInJson + "}", true)
          val deletedTaggedValidatedEvent = new TaggedValidatedEvent(lineage,
            IdGenerator.nextEventId(appContext.config.oxygen.JOB_ID, TaskContext.getPartitionId()),
            "",
            Payload(Map.empty), Array.empty[QualityScore],
            Array.empty[ObjectError],
            tce.eventType, tce.minFrequency,
            tce.asOfDate, tce.didCreateAsOfDateFromReceivedDate,
            tce.subscriberRelation,
            tce.subscriber,
            Array.empty[ObjectError],
            Array.empty[ObjectError],
            keys = "",
            inferredAsDeleted,
            tce.fileTag,
            tce.notificationLoad
          )
          deletedTaggedValidatedEvent
        })
      eventsToBeDeleted
    }
  }
}
