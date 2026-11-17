//package com.experian.edf.oxygen.validation
//
//import com.experian.edf.oxygen.base.OxygenService
//import com.experian.edf.oxygen.configuration.configs.OxygenPipelineConfiguration
//import com.experian.edf.oxygen.context.SparkOxygenAppContext
//import com.experian.edf.oxygen.domain.internal.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
//import com.experian.edf.oxygen.pinning.PinningIOUtils
//import com.experian.edf.oxygen.utils.{JsonUtils, OxygenFileUtils}
//import com.google.gson.JsonObject
//import org.apache.spark.broadcast.Broadcast
//import org.apache.spark.rdd.RDD
//import org.apache.spark.sql.AnalysisException
//import org.slf4j.LoggerFactory.getLogger
//
//import scala.annotation.tailrec
//import scala.collection.mutable
//
//// todo: the below CuratedEventReader has been introduced to make the
//// testing of this class easier without disrupting the general API
//// and requiring wider refactoring.
//// In the longer term, it would be good to instantiate the CuratedEventReaderImpl
//// with the PinningIOUtils and OxygenPipelineConfiguration dependencies, as they
//// are not otherwise used by the ContextualValidatorWithCuratedEvents class.
//// i.e.:
////
////  trait CuratedEventReader {
////    def readCuratedEvent(): RDD[TaggedCuratedEventWithMap]
////  }
////
////
////  class CuratedEventReaderImpl(
////    pinningIOUtils: PinningIOUtils,
////    pipelineConfiguration: OxygenPipelineConfiguration
////  ) extends curatedEventReader {
////      ...
////   }
//
//private[validation] trait CuratedEventReader {
//  def readCuratedEvent(
//    pinningIOUtils: PinningIOUtils,
//    pipelineConfiguration: OxygenPipelineConfiguration
//  ): RDD[TaggedCuratedEventWithMap]
//}
//
//private[validation] object CuratedEventReaderImpl extends CuratedEventReader {
//  override  def readCuratedEvent(
//    pinningIOUtils: PinningIOUtils,
//    pipelineConfiguration: OxygenPipelineConfiguration
//  ): RDD[TaggedCuratedEventWithMap] = {
//    val storageFormat = pipelineConfiguration.CONTEXTUAL_VALIDATION_FORMAT
//    pinningIOUtils.readCuratedEvents(storageFormat)
//  }
//}
//
//class ContextualValidatorWithCuratedEvents(
//  appContext: SparkOxygenAppContext,
//  curatedEventReader: CuratedEventReader = CuratedEventReaderImpl
//) extends OxygenService(appContext)  {
//  @transient private lazy val logger = getLogger(this.getClass)
//
//  val pinningIOUtils = new PinningIOUtils(fileUtils, appContext)
//
//  private[validation] def performContextualValidation(
//    pendingValidatedEvents: RDD[TaggedValidatedEvent],
//    processor: Broadcast[ValidationProcessor],
//    pipelineConfiguration: OxygenPipelineConfiguration
//  ): RDD[TaggedValidatedEvent] = {
//
//    val contextColumns = processor.value.contextPredicates
//
//    if (contextColumns == Map.empty) {
//      pendingValidatedEvents
//    }
//    else {
//      val taggedCuratedEvents = {
//        try {
//          curatedEventReader.readCuratedEvent(pinningIOUtils, pipelineConfiguration)
//        } catch {
//          case e: AnalysisException if e.getMessage.startsWith("Path does not exist")  => {
//            logger.info(
//              "No new events found for contextual validation. Continuing with " +
//              "pending validated events only"
//            )
//            appContext.sc.emptyRDD[TaggedCuratedEventWithMap]
//          }
//        }
//      }.cache
//
//      val subscriberList = pendingValidatedEvents.map(pve => pve.subscriber).collect().toList
//      val curatedEvents = taggedCuratedEvents.filter(tce => subscriberList.contains(tce.subscriber))
//
//      validateContextually(
//        contextColumns,
//        processor,
//        pendingValidatedEvents,
//        curatedEvents
//      )
//    }
//  }
//
//  @tailrec
//  private def validateContextually(
//    contextPredicates: Map[String, Array[String]],
//    validationProcessor: Broadcast[ValidationProcessor],
//    pendingValidatedEvents: RDD[TaggedValidatedEvent],
//    taggedCuratedEvents: RDD[TaggedCuratedEventWithMap]
//  ): RDD[TaggedValidatedEvent] = {
//    if (contextPredicates.isEmpty) pendingValidatedEvents
//    else {
//      val head = contextPredicates.head
//      val tail = contextPredicates.tail
//
//      val contextName = head._1
//      val fields = head._2
//
//      val contextuallyValidatedEvents = validateForAContext(
//        validationProcessor,
//        contextName,
//        fields,
//        pendingValidatedEvents,
//        taggedCuratedEvents
//      )
//
//      validateContextually(tail, validationProcessor, contextuallyValidatedEvents, taggedCuratedEvents)
//    }
//  }
//
//  private def validateForAContext(
//    validationProcessor: Broadcast[ValidationProcessor],
//    contextName: String,
//    contextFields: Array[String],
//    pendingValidatedEvents: RDD[TaggedValidatedEvent],
//    taggedCuratedEvents: RDD[TaggedCuratedEventWithMap]
//  ): RDD[TaggedValidatedEvent] = {
//
//    val empty1 = mutable.ListBuffer[TaggedValidatedEvent]()
//    val pendingValidatedEventsWithJoinKey = pendingValidatedEvents
//      .map(x => {
//        val joinKey = x.subscriber + ":" + contextFields.map(fieldName =>
//          JsonUtils.fromJson(x.validatedRecordJson, classOf[JsonObject]).get(fieldName).toString.replaceAll("^\"|\"$", "")).mkString(":")
//        (joinKey, x)
//      })
//      .aggregateByKey(empty1)(
//        (lb, v) => lb += v,
//        (lb1, lb2) => lb1 ++= lb2)
//      .map(x => {
//        (x._1, x._2.toArray)
//      })
//
//    val empty2 = mutable.ListBuffer[TaggedCuratedEventWithMap]()
//    val taggedCuratedEventsWithJoinKey = taggedCuratedEvents
//      .map(x => {
//        val joinKey = x.subscriber + ":" + contextFields.map(fieldName => x.curatedRecordJson.get(fieldName)).mkString(":")
//        (joinKey, x)
//      })
//      .aggregateByKey(empty2)(
//        (lb, v) => lb += v,
//        (lb1, lb2) => lb1 ++= lb2)
//      .map(x => {
//        (x._1, x._2.toArray)
//      })
//
//    val contextuallyValidatedEvents = taggedCuratedEventsWithJoinKey
//      .rightOuterJoin(pendingValidatedEventsWithJoinKey)
//      .map(x => {
//        val taggedCuratedEvents = x._2._1.getOrElse(Array.empty)
//        val pendingTaggedValidatedEvents = x._2._2
//
//        val contextuallyValidatedEventsWithErrorsAndQS = validationProcessor.value.contextuallyValidate(pendingTaggedValidatedEvents,
//          contextName, taggedCuratedEvents)
//
//        contextuallyValidatedEventsWithErrorsAndQS.map(x => {
//          val contextuallyValidatedEvent = x._1
//          val qualityScores = x._2
//          val errors = x._3
//          val newTaggedValidatedEvent = new TaggedValidatedEvent(contextuallyValidatedEvent.lineage,
//            contextuallyValidatedEvent.eventId, contextuallyValidatedEvent.validatedRecordJson,
//            contextuallyValidatedEvent.payload, contextuallyValidatedEvent.qualityScores ++ qualityScores,
//            contextuallyValidatedEvent.errorCodes ++ errors,
//            contextuallyValidatedEvent.eventType, contextuallyValidatedEvent.minFrequency,
//            contextuallyValidatedEvent.asOfDate, contextuallyValidatedEvent.didCreateAsOfDateFromReceivedDate,
//            contextuallyValidatedEvent.subscriberRelation,
//            contextuallyValidatedEvent.subscriber,
//            contextuallyValidatedEvent.headerErrors,
//            contextuallyValidatedEvent.footerErrors,
//            contextuallyValidatedEvent.keys)
//          newTaggedValidatedEvent
//        })
//
//      }).flatMap(x => x)
//
//    contextuallyValidatedEvents
//  }
//
//  // todo: this doesn't appear to be used anywhere and I can't find any references
//  // to it in docs, so I'm assuming it's not part of any public api?
//  // If that's the case, can we remove this please?
//  private[validation] def currentCuratedDataset(
//    pipelineConfiguration: OxygenPipelineConfiguration,
//    appContext: SparkOxygenAppContext
//  ): List[String] = {
//    val curationDir = pipelineConfiguration.stage.curation.get.paths.curatedDir
//    val fileUtils = OxygenFileUtils.create(appContext)
//    fileUtils.listFilenames(curationDir)
//  }
//}
