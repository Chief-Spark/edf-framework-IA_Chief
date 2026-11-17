package com.experian.edf.oxygen.curation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.CurationRunMode
import com.experian.edf.oxygen.configuration.configs.CurationRunMode._
import com.experian.edf.oxygen.configuration.configs.stages.CurationStageConfiguration
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.global.processmngt.ProcessInstruction
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.utils.OxygenProcessorCache
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.FilenameUtils._
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.apache.spark.TaskContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.{Dataset, Encoder, SparkSession}
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

import java.io.File
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/30/19 at 4:12 PM
 */
class SparkCurator(appContext: SparkOxygenAppContext) extends Curator(appContext) {

  @transient override lazy val logger: Logger = getLogger(this.getClass)
  val spark: SparkSession = appContext.sparkSession
  private val inferDeleteProcessor = new InferredDeletedCurationProcessor(CurationProcessorContext(OxygenLookupService.getServiceB.value))
  private val enableOxygenDefaultProcessor = appContext.config.oxygen.isFeatureActive(Constants.features.ENABLE_DEFAULT_CURATION_PROCESSOR)
  private val curationConfigs: CurationStageConfiguration = oxygenConf.oxygen.stage.curation.get
  private val _cachedProcessors = CuratorUtils.getProcessorMap(curationConfigs.configs, CurationProcessorContext(OxygenLookupService.getServiceB.value), enableOxygenDefaultProcessor)
  private val _processorCache = new OxygenProcessorCache[CurationProcessor](_cachedProcessors)
  private val ioHelper = new CuratorIOUtils(appContext, fileUtils)
  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")
  private val filterOutFilesPatterns = curationConfigs.excludes
  //private val reasonability = curationConfigs.reasonability
  //private val metricsReasonability = new ReasonabilityProcess()
  private val defaultOutputStructure = oxygenConf.configMap.get("oxygen.features.enable.defaultOutputStructure").getOrElse("false").toBoolean

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.CURATION,
    source = MetricsConstants.Sources.CURATOR,
    orchestrationSource = oxygenConf.oxygen.stage.curation.get.orchestration.get.source,
    orchestrationTarget = oxygenConf.oxygen.stage.curation.get.orchestration.get.target,
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId)
  )

  oxygenConf.oxygen.stage.consistency.get.paths.domainDictionaryFile.foreach(each => println("Each DD File : " + each))
  logger.error("Head Domain Dict File :" + oxygenConf.oxygen.stage.consistency.get.paths.domainDictionaryFile.head)

  private val processorCacheBroadcast: Broadcast[OxygenProcessorCache[CurationProcessor]] = appContext.sc.broadcast(_processorCache)


  @throws(classOf[OxygenException])
  override def runInternal(runMode: CurationRunMode): Any = {
    startService(Map("runMode" -> runMode.toString.toLowerCase))
    logger.error(s"defaultOutputStructure : $defaultOutputStructure")
    runMode match {
      case FLOW => performFlowCuration()
      case STOCK =>
        performStockCuration()
        performEventSyncFull()
      case EVENTSYNC => performEventSyncFull()
      case INCSYNC => performEventSyncIncremental()
    }
    if (!defaultOutputStructure) removeDefaultOutputStructure
    endService()
  }

  def removeDefaultOutputStructure(): Unit = {
    val curatedDir = curationConfigs.paths.curatedDir
    val acceptedDir = appContext.config.oxygen.ACCEPTED_DIRECTORY
    val userHomeDir = oxygenConf.configMap.getOrElse("oxygen.application.paths.userHome", "")
    if (userHomeDir != "" &
      ((!curatedDir.contains(userHomeDir)) & (!acceptedDir.contains(userHomeDir))) &
      fileUtils.exists(userHomeDir)) {
      fileUtils.deleteFileOrFolder(userHomeDir)
      logger.info("Default output structure removed.")
    }
  }

  private def performEventSyncIncremental(): Unit = {
    val lambdaSyncConfig = curationConfigs.paths.lambdaSync
    val offsetData: Option[String] = Try(fileUtils.readFileContentAsString(curationConfigs.paths.lambdaSync.offsetSource )) match {
      case Success(value) => Option(value)
      case Failure(exception) => logger.error("Event Sync Skipped : " + exception.getMessage); None
    }
    if (offsetData.isDefined) {
      val syncedFiles: List[String] = fileUtils.listFilenames(lambdaSyncConfig.dataSource).filter(!_.contains("_spark_metadata"))
      import appContext.sparkSession.implicits._
      val collectedFiles: List[String] = spark.read.text(curationConfigs.paths.lambdaSync.syncMetadata).map(_.getAs[String]("value")).collect().toList
      val filesToCollect = syncedFiles.diff(collectedFiles)
      if (filesToCollect.nonEmpty) {
        curateAndSaveData(filesToCollect, curationConfigs.paths.lambdaSync.dataTarget + File.separator + System.currentTimeMillis(), runMode = "eventSync")
        fileUtils.writeToFile(curationConfigs.paths.lambdaSync.syncMetadata + File.separator + System.nanoTime(), filesToCollect.mkString("\n"))
        fileUtils.writeToFile(curationConfigs.paths.lambdaSync.offsetTarget, offsetData.get)
      }
    }
  }

  private def performEventSyncFull(): Unit = {
    val lambdaSyncConfig = curationConfigs.paths.lambdaSync
    val offsetData: Option[String] = Try(fileUtils.readFileContentAsString(curationConfigs.paths.lambdaSync.offsetSource)) match {
      case Success(value) => Option(value)
      case Failure(exception) => logger.error("Event Sync Skipped : " + exception.getMessage); None
    }
    Try(fileUtils.moveFile(lambdaSyncConfig.dataTarget, lambdaSyncConfig.syncArchived + File.separator + "archived-on-" + System.currentTimeMillis()))
    if (offsetData.isDefined) {
      val syncedFiles: List[String] = fileUtils.listFilenames(lambdaSyncConfig.dataSource).filter(!_.contains("_spark_metadata"))
      if (syncedFiles.nonEmpty) {
        curateAndSaveData(syncedFiles, curationConfigs.paths.lambdaSync.dataTarget, runMode = "eventSyncFull")
        fileUtils.writeToFile(curationConfigs.paths.lambdaSync.offsetTarget, offsetData.get)
      }
    }
  }


  /**
   * Does Full Curation - Output will be saved under oxygen-app/curation/full_curation
   * Data in current curation directory will be archived under oxygen/curarion/archived/curated-archived-on-{timestamp}
   */
  private def performStockCuration(): Unit = {
    val instructions = orchestrator.retrieveAllDatasetsToProcess().getOrElse(List.empty[ProcessInstruction])
    val targetLocation = curationConfigs.paths.curatedDir + File.separator + "stock"
    try {
      curateAndSaveData(List(appContext.config.oxygen.ACCEPTED_DIRECTORY + File.separator + "*"), targetLocation, archive = true, runMode = "stock")
      logger.info(s"Full curation completed Successfully , Saved at $targetLocation")
      instructions.foreach(instruction => orchestrationHelper.markDatasetAsSuccess(instruction.jobId(), instruction.datasetId(), instruction.location(), comments = s"Curated Successfully(as part of full Curation - ${orchestrationContext.runId})", target = Option(instruction.target())))
    } catch {
      case e: Exception =>
        val errorMessage = s"Full curation FAILED - ${e.getMessage}"
        logger.error(errorMessage, e)
        instructions.foreach(instruction => orchestrationHelper.markDatasetAsError(instruction.jobId(), instruction.datasetId(), instruction.location(), comments = errorMessage, target = Option(instruction.target())))
        throw new OxygenException("Unable to perform Full Curation", e)
    }
  }

  /**
   * Picks up one Process Instruction at a time and processes file by file.
   * Tail Recursive method
   */
  @tailrec
  private def performFlowCuration(): Unit = {
    val maybeInstruction = orchestrator.retrieveOneDatasetToProcess()
    if (maybeInstruction.isDefined) {
      curateProcessInstruction(maybeInstruction.get, "flow")
      performFlowCuration()
    }
  }

  /**
   * Performs curation for a specific `ProcessInstruction`
   *
   * @param processInstruction Instruction to process
   * @return Status of execution
   */
  def curateProcessInstruction(processInstruction: ProcessInstruction, runMode: String): Boolean = {

    val isThisFileFilterOut = filterOutFilePattern(FilenameUtils.getName(processInstruction.getLocation))

    if (processInstruction.getLocation.contains("errorInEmptyFileBody")) {
      val message = s"Curation Skipped: errorInEmptyFileBody : " + processInstruction.location()
      logger.info(escapeJava(message))
      orchestrationHelper.markDatasetAsNoop(processInstruction.getJobId, processInstruction.datasetId(), processInstruction.getLocation, message, target = Option(processInstruction.target))
      return false
    }
    if (isThisFileFilterOut) {
      val message = s" Exclusion Curation Flow Skipped [MarkedAsSuccess] : " + processInstruction.location()
      logger.info(escapeJava(message))
      orchestrationHelper.markDatasetAsSuccess(processInstruction.jobId(), processInstruction.datasetId(), processInstruction.location(), target = Option(processInstruction.target()))
      return true
    }
    if (_processorCache.getProcessor(removeExtension(processInstruction.location)).isDefined || processInstruction.location.contains(appContext.config.oxygen.LAMBDASYNC_FILE_SUFFIX)) { // Is Processor defined and hfEvents
      logger.debug(s"Curating dataset : ${processInstruction.datasetId}, Location: ${processInstruction.location()}")
      orchestrationHelper.markDatasetAsInProgress(processInstruction.jobId(), processInstruction.datasetId(), processInstruction.location())
      val fileNameWithExtension = getName(processInstruction.location())
      try {
        curateAndSaveData(List(processInstruction.location), curationConfigs.paths.curatedDir + File.separator + runMode + File.separator + fileNameWithExtension, runMode = runMode)
        logger.debug(s"Curation Success : ${processInstruction.location}")
        orchestrationHelper.markDatasetAsSuccess(processInstruction.jobId(), processInstruction.datasetId(), processInstruction.location(), target = Option(processInstruction.target()))
        true
      } catch {
        case exception: Exception =>
          logger.error(s"Curation Failed : ${processInstruction.location} - ${exception.getMessage}", exception)
          orchestrationHelper.markDatasetAsError(processInstruction.jobId(), processInstruction.datasetId(), processInstruction.location(), s"Curation Error: ${exception.getMessage}", target = Option(processInstruction.target()))
          false
      }
    } else {
      val errorMessage = s"Curation Processor Not Found : ${processInstruction.location}"
      logger.error(errorMessage)
      orchestrationHelper.markDatasetAsError(processInstruction.jobId(), processInstruction.datasetId(), processInstruction.location(), s"Curation Error: $errorMessage", target = Option(processInstruction.target()))
      false
    }
  }

  /**
   * Curates and saves data
   *
   * @param source
   * @param target
   * @param archive
   * @return
   */
  def curateAndSaveData(source: List[String], target: String, archive: Boolean = false, dropDuplicates: Boolean = false, runMode: String): Boolean = {
    try {
      implicit val taggedCuratedEventEncoder: Encoder[TaggedCuratedEventWithMap] =
        org.apache.spark.sql.Encoders.kryo[com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap]
      val curatedDataDeduped: Dataset[TaggedCuratedEventWithMap] = if (dropDuplicates)
        curateData(source, runMode).dropDuplicates("eventId")
      else curateData(source, runMode)

      val curatedData: Dataset[TaggedCuratedEventWithMap] = if (emitStockMetricsEnabled) {
        curatedDataDeduped.mapPartitions(
          eachByPartition => {
            val eachByPartitionList = eachByPartition.toList
            val map = mutable.HashMap[String, Any]()
            map += ("runMode" -> runMode)
            map += ("partitionId" -> TaskContext.getPartitionId())
            emitStockMetric("eventCountPerPartition", "eventCountPerPartition", eachByPartitionList.size, map.toMap)
            eachByPartitionList.toIterator
          })
      } else {
        curatedDataDeduped
      }

      if (oxygenConf.oxygen.LAMBDA_TEST_MODE)
      {
        val curatedDataCount = curatedData.count()
        addServiceInfoMetric("curatedEventCount", curatedDataCount)
      }
      ioHelper.saveCuratedData(curatedData, target, archive)
    } catch {
      case exception: Exception => throw new OxygenException(s"Curation Failed : ${exception.getMessage}", exception)
    }
  }

  def ccToMap(cc: AnyRef) =
    (Map[String, Any]() /: cc.getClass.getDeclaredFields) {
      (a, f) =>
        f.setAccessible(true)
        a + (f.getName -> f.get(cc))
    }

  def curateData(location: List[String], runMode : String) : Dataset[TaggedCuratedEventWithMap] = {
    val taggedValidatedEvents: Dataset[TaggedValidatedEvent] =  ioHelper.readTaggedValidatedEvent(fileUtils, location)
    implicit val taggedCuratedEventEncoder: Encoder[TaggedCuratedEventWithMap] =
      org.apache.spark.sql.Encoders.kryo[com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap]
    val changeCurationFrequency = oxygenConf.oxygen.isFeatureActive(Constants.features.ENABLE_CHANGE_FREQUENCY_ON_CURATION)
    val validated = taggedValidatedEvents.mapPartitions(recIter => {
      if (recIter.hasNext) {
        val recIterList: List[TaggedValidatedEvent] = recIter.toList
        val processingRecords = ListBuffer[TaggedCuratedEventWithMap]()
        var processor: Option[CurationProcessor] = null
        recIterList.foldLeft("")((previousFileName, event) => {
          if (previousFileName != event.lineage.fileName) {
            val processorCache = processorCacheBroadcast.value
            processor = if (event.inferredAsDeleted != null && event.inferredAsDeleted.inferredDeletedFlag)
              Option(inferDeleteProcessor)
            else
              processorCache.getProcessor(event.lineage.fileName)
          }
          if (processor.isDefined) {
            try {
              processingRecords += curateTaggedEvent(processor.get, event, None, changeCurationFrequency)
            } catch {
              case exception: Exception =>
                exception.printStackTrace()
                logger.warn(s"Curation of Event : ${event.eventId} failed IGNORING event. Error Message {}", exception.getMessage)
            }
          }
          event.lineage.fileName
        })
        processingRecords.toIterator
      }
      else {
        Iterator()
      }
    }
    )
    /*val map = mutable.HashMap[String, Any]()
    map += ("runMode" -> runMode)
    map += ("partitionId" -> TaskContext.getPartitionId())
    emitStockMetric("eventCountByPartition","eventCountByPartition", validated.rdd.count(), map.toMap)*/
    validated
  }

  def emitStockMetric(name: String, key: String, value: Long, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.CURATION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: Double, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.CURATION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.CURATION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitFlowMetric(name: String, datasetId: String, key: String, value: Long, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.CURATION, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitFlowMetric(name: String, datasetId: String, key: String, value: Double, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.CURATION, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitFlowMetric(name: String, datasetId: String, key: String, value: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.CURATION, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  private def filterOutFilePattern(fileName: String): Boolean = {

    for (patter <- filterOutFilesPatterns ){
      if (removeFileExtension(fileName) matches patter._1)
        return true
    }
    false
  }
  private[this] def removeFileExtension(fullPathFileName: String): String = {
    if (fullPathFileName.contains("errorIn"))
      removeExtension(removeExtension(fullPathFileName))
    else
      removeExtension(fullPathFileName)
  }


}