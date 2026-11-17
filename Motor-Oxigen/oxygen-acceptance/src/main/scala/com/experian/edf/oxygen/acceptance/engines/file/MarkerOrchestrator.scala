package com.experian.edf.oxygen.acceptance.engines.file

//import com.experian.edf.oxygen.base.OxygenService
import com.experian.edf.oxygen.acceptance.base.OxygenService
import com.experian.edf.oxygen.global.processmngt.constants.PmConstants
import com.experian.edf.oxygen.global.processmngt.{ProcessInstruction, ProcessUpdate}
import com.experian.edf.oxygen.logging.DatasetProcessLoggingHelper._
import com.experian.edf.oxygen.module.logging.metrics.factories.DatasetStatusEventsFactory
import com.experian.edf.oxygen.module.logging.{OxygenEmitterFactory, OxygenMetricsEmitter}
import com.experian.edf.oxygen.orchestration.{OrchestrationContext, OxygenOrchestrator}
import com.experian.edf.oxygen.utils.ObjectUtils.using
import com.experian.edf.oxygen.utils.{FileUtils, JsonUtilExperimental, Utils}
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

import java.io.File
import scala.collection.mutable.ListBuffer


/**
 *
 * @author c15627a - Abraham, Thomas
 *
 **/
class MarkerOrchestrator(override val orchestrationContext: OrchestrationContext, oxygenService: OxygenService) extends OxygenOrchestrator {

  @transient lazy val logger: Logger = getLogger(this.getClass)
  val puTopic = oxygenService.oxygenConf.oxygen.getKafkaLoggerTopic.getOrElse("puTopic", "")


  val markerHome: String = oxygenService.oxygenConf.oxygen.MARKER_HOME
  val fileUtils: FileUtils = oxygenService.fileUtils
  val kafkaMsg = new KafkaMsg(oxygenService.oxygenConf);

  val IN_PROGRESS = "inprogress"

  private val metricEmitter: OxygenMetricsEmitter = OxygenEmitterFactory.getMetricEmitter

  override def retrieveOneDatasetToProcess(): Option[ProcessInstruction] = {
    if (orchestrationContext.orchestrationSource.isDefined) {
      try {
        val fileList: List[(String, String)] = sourceDirectoryList.flatMap(sourceDirectory => {
          fileUtils.createDirectory(sourceDirectory)
          scala.util.Random.shuffle(fileUtils.listFilePathAndName(sourceDirectory, recursive = false).filter(x => x._2.matches("\\d*$")))
        })
        if (fileList.nonEmpty) {
          val file = fileList.head
          val processUpdateString = fileUtils.readFileContentAsString(file._1) // TODO: Need to find a better way for doing this.
          val processUpdate: ProcessUpdate = JsonUtilExperimental.fromJson[ProcessUpdate](processUpdateString)
          val inProgressDirectory = targetDirectory(processUpdate) + File.separator + IN_PROGRESS
          val inProgressFile = inProgressDirectory + File.separator + processUpdate.datasetId()
          fileUtils.createDirectory(inProgressDirectory)
          if (fileUtils.moveFile(file._1, inProgressFile)) {
            val startProcessingMessage = startDatasetProcessing(processUpdate.datasetId(), orchestrationContext.action, processUpdate.location(), processUpdate.jobId(), s"${orchestrationContext.action} is in Progress")
            logger.debug(startProcessingMessage)
            //            puLogger.datasetInfos(startProcessingMessage)
            logger.debug("send message from FM" + puTopic + " msg " + startProcessingMessage)
            kafkaMsg.sendRecordWithProperties(oxygenService.oxygenConf, puTopic, startProcessingMessage, processUpdate.datasetId())

            //val inProgressEvent = DatasetStatusEventsFactory.inProgress(processUpdate.datasetId(), orchestrationContext.source, orchestrationContext.action, orchestrationContext.runId, processUpdate.location())
            //metricEmitter.emitCritical(inProgressEvent)

            Option(processUpdate.toProcessInstruction)
          } else None
        }
        else None

      } catch {
        case e: Exception =>
          logger.warn(s"Not able to find directory: Please ensure that you have running the pipeline from beginning.... ${e.getMessage}")
          None
      }
    }
    else None
  }

  override def retrieveAllDatasetsToProcess(): Option[List[ProcessInstruction]] = {
    if (orchestrationContext.orchestrationSource.isDefined) {
      try {
        val fileList: List[(String, String)] = sourceDirectoryList.flatMap(sourceDirectory => {
          fileUtils.createDirectory(sourceDirectory)
          scala.util.Random.shuffle(fileUtils.listFilePathAndName(sourceDirectory, recursive = false).filter(x => x._2.matches("\\d*$")))
        })
        val instructions = new ListBuffer[ProcessInstruction]
        fileList.foreach(file => {
          val processUpdateString = fileUtils.readFileContentAsString(file._1) // TODO: Need to find a better way for doing this.
          val processUpdate = JsonUtilExperimental.fromJson[ProcessUpdate](processUpdateString)
          val inProgressDirectory = targetDirectory(processUpdate) + File.separator + IN_PROGRESS
          val inProgressFile = inProgressDirectory + File.separator + processUpdate.datasetId()
          val startProcessingMessage = startDatasetProcessing(processUpdate.datasetId(), orchestrationContext.action, processUpdate.location(), processUpdate.jobId(), s"${orchestrationContext.action} is in Progress")
          logger.debug(startProcessingMessage)
          //          puLogger.datasetInfos(startProcessingMessage)
          kafkaMsg.sendRecordWithProperties(oxygenService.oxygenConf, puTopic, startProcessingMessage, processUpdate.datasetId())

          val inProgressMetric = DatasetStatusEventsFactory.inProgress(processUpdate.datasetId(), orchestrationContext.source, orchestrationContext.action, orchestrationContext.runId, processUpdate.location())
          metricEmitter.emitInfo(inProgressMetric)

          fileUtils.createDirectory(inProgressDirectory)
          if (fileUtils.moveFile(file._1, inProgressFile)) {
            instructions.append(processUpdate.toProcessInstruction)
          }
        })
        return Option(instructions.toList)
      } catch {
        case e: Exception => logger.error(s"Not able to find directory: Please ensure that you have running the pipeline from beginning.... ${e.getMessage}")
      }
    }
    None
  }

  override protected def sendUpdate(processUpdate: ProcessUpdate): Boolean = {
    //If the file is from discovery stage, handle it differently
    if (processUpdate.processType().equals(PmConstants.processType.PROCESS_FILE_DISCOVERY)) {
      val discoveredDirectory = targetDirectory(processUpdate)
      val discoveredInProgressDirectory = discoveredDirectory + File.separator + IN_PROGRESS
      fileUtils.createDirectory(discoveredDirectory)
      fileUtils.createDirectory(discoveredInProgressDirectory)
      if (!(fileUtils.isFileProcessed(discoveredInProgressDirectory, processUpdate.location()) || fileUtils.isFileProcessed(discoveredDirectory, processUpdate.location()))) {
        val fileId = if (processUpdate.datasetId() != null && processUpdate.datasetId().matches("\\d*$")) processUpdate.datasetId() else Utils.getUniqueNumber(scala.util.Random.nextInt(1000)).toString
        processUpdate.datasetId(fileId).jobId(fileId)
        val discoveryLogMessage = discoverNewFile(fileId, orchestrationContext.action, processUpdate.location(), processUpdate.jobId())
        logger.debug(discoveryLogMessage)
        //        puLogger.datasetInfos(discoveryLogMessage)
        kafkaMsg.sendRecordWithProperties(oxygenService.oxygenConf, puTopic, discoveryLogMessage, processUpdate.datasetId())

        // THOMAS: LoggingRewamp : 072620
        val discoverMetric = DatasetStatusEventsFactory.discover(processUpdate.datasetId(), orchestrationContext.source, orchestrationContext.action, orchestrationContext.runId, processUpdate.location(), Map("dataHomePath" -> processUpdate.location()))
        metricEmitter.emitInfo(discoverMetric)

        writeFile(processUpdate, targetDirectoryFileName(discoveredDirectory, fileId))
      } else {
        logger.info(s"${processUpdate.location()} already discovered. Skipping...")
      }
    }
    // If the process update is END
    else if (processUpdate.processType().equals(PmConstants.processType.END)) {
      val endProcessingLogMessage = endDatasetProcessing(processUpdate.datasetId(), orchestrationContext.action, processUpdate.location(), processUpdate.jobId(), processUpdate.status(), processUpdate.comments())
      logger.debug(endProcessingLogMessage)

      kafkaMsg.sendRecordWithProperties(oxygenService.oxygenConf, puTopic, endProcessingLogMessage, processUpdate.datasetId())


      val inProgressFile: Option[String] = if (processUpdate.target() != null) // TODO: change this null
      Option(markerHome + File.separator + processUpdate.target() + File.separator + IN_PROGRESS + File.separator + processUpdate.datasetId())
        else None

      if (processUpdate.status().equals(PmConstants.status.SUCCESS)) {
        logger.debug(s"Dataset ${processUpdate.datasetId} has successfully processed")
        if (orchestrationContext.orchestrationTarget.isDefined) {
          val orchestrationTargetList = orchestrationContext.orchestrationTarget.get //processUpdate.target().asScala.toList
          orchestrationTargetList.foreach(target => {
            processUpdate.setTarget(target)
            writeFile(processUpdate, targetDirectoryFileName(targetDirectoryWithMarkerHome(target), processUpdate.datasetId()))
          })
        } else {
          logger.debug(s"There is no target defined for stage ${processUpdate.stage()} in configuration. So marker will not be written for ${processUpdate.datasetId()} for further processing")
        }
      }
      if (inProgressFile.isDefined) {
        if (processUpdate.status.equals(PmConstants.status.SUCCESS)) {
          fileUtils.deleteFileOrFolder(inProgressFile.get)
        } else {
          val statusDirectory = markerHome + File.separator + processUpdate.target() + File.separator + processUpdate.status().toLowerCase

          fileUtils.createDirectory(statusDirectory)
          fileUtils.deleteFileOrFolder(inProgressFile.get)
          writeFile(processUpdate, statusDirectory + File.separator + processUpdate.datasetId())
          if (processUpdate.status()!=null && processUpdate.status().equals("NOOP")) {
            val statusAnalyticsDirectory = markerHome + File.separator + processUpdate.target() + File.separator +"analytics-"+processUpdate.status().toLowerCase

            fileUtils.createDirectory(statusAnalyticsDirectory)
            writeFile(processUpdate, statusAnalyticsDirectory + File.separator + processUpdate.datasetId())
          }
        }
      }
    }
    //else just returning true
    true
  }

  private[this] def targetDirectory(processUpdate: ProcessUpdate): String = markerHome + File.separator + processUpdate.target()

  private[this] def targetDirectoryWithMarkerHome(target: String): String = markerHome + File.separator + target

  private[this] def targetDirectoryFileName(targetDirectory: String, fileName: String): String = targetDirectory + File.separator + fileName

  private[this] val sourceDirectoryList: List[String] = if (orchestrationContext.orchestrationSource.isDefined) orchestrationContext.orchestrationSource.get.map(each => markerHome + File.separator + each) else List.empty[String]

  private[file] def writeFile(processUpdate: ProcessUpdate, fileName: String): Boolean = {
    try {
      val fileContent = JsonUtilExperimental.toJson(processUpdate)
      val in = IOUtils.toInputStream(fileContent)
      using(in) { in => fileUtils.writeToFile(fileName, in) }
      true
    } catch {
      case e: Exception =>
        logger.error(s"Error while writing Marker File : ${e.getMessage}", e)
        false
    }
  }

  override def discoverDataset(processUpdate: ProcessUpdate): Boolean = {
    sendUpdate(processUpdate)
  }
}