package com.experian.edf.oxygen.validation.engines

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.global.processmngt.ProcessUpdate
import com.experian.edf.oxygen.global.processmngt.constants.PmConstants
import com.experian.edf.oxygen.global.utils.IdGenerator
import com.experian.edf.oxygen.io.OxygenIO
import com.experian.edf.oxygen.logging.DatasetProcessLoggingHelper
import com.experian.edf.oxygen.module.logging.OxygenEmitterFactory
import com.experian.edf.oxygen.module.logging.metrics.factories.DatasetStatusEventsFactory
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.apache.spark.rdd.RDD
import org.slf4j.LoggerFactory._

import java.io.File
import scala.collection.mutable

/**
 *
 * @param appContext
 */
class SparkDiscovererWithMarkers(appContext: SparkOxygenAppContext) extends Discoverer(appContext) {

  @transient override lazy val logger = getLogger(this.getClass)
  private val oxygenIO = new OxygenIO(appContext.sparkSession)
  lazy val kafkaMsg = new KafkaMsg(oxygenConf);
  val puTopic: Option[String] = oxygenConf.oxygen.getKafkaLoggerTopic.get("puTopic")
  val sparkSession = appContext.sparkSession // Spark session

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext("Discovery", "Discoverer", None, None, oxygenConf, Option(appContext.sc.applicationId))

  override def run(): List[String] = {

    val map = mutable.HashMap[String, Any]()
    map += ("processType" -> "start")

    startService(map.toMap)

    val files = try {
      discover(oxygenConf.oxygen.DATA_HOMES)
    } finally {
      orchestrator.close()
    }

    //endService()
    map.remove("processType")
    map += ("processType" -> "end")

    endService(map.toMap)
    files
  }

  def discover(dataHomes: List[String]): List[String] = {
    val incomingFiles: List[(String, String)] = dataHomes.flatMap(home => {
      fileUtils.listFilePathAndName(home)
    }).filter(file => !file._1.endsWith("edftmp") && !file._2.startsWith("."))
    logger.debug("Marker File Names")
    val inProgressFiles = discoveredAndInProgressFiles()
    inProgressFiles.foreach(logger.debug)
    val incomingFileName = incomingFiles.map(_._1)
    logger.debug("Incoming File Names")
    incomingFileName.foreach(logger.debug)
    val newFiles = incomingFileName.diff(inProgressFiles)
    logger.debug("Diff File Names")
    newFiles.foreach(file => {
      logger.debug(s"New File Discovered :$file")
      val id = IdGenerator.getUniqueId().toString

      if (oxygenConf.oxygen.DISCOVERY_ORCHESTRATION.target.isDefined)
        oxygenConf.oxygen.DISCOVERY_ORCHESTRATION.target.get.foreach(target => {
          val processUpdate = DatasetProcessLoggingHelper.discoverNewFile(id, PmConstants.stages.DISCOVERY, file, orchestrationContext.runId, target)

          val discoveryMetric = DatasetStatusEventsFactory.discover(id, orchestrationContext.source, orchestrationContext.action, orchestrationContext.runId, file)
          OxygenEmitterFactory.getMetricEmitter.emitCritical(discoveryMetric)

          val in = org.apache.commons.io.IOUtils.toInputStream(processUpdate)
          if (puTopic.isDefined) {
            kafkaMsg.sendRecordWithProperties(oxygenConf, puTopic.get, processUpdate, id)
          }
          fileUtils.writeToFile(appContext.config.oxygen.MARKER_HOME + File.separator + target + File.separator + id, in)
        })
    })
    newFiles
  }

  def discoveredAndInProgressFiles(): List[String] = {
    if (oxygenConf.oxygen.DISCOVERY_ORCHESTRATION.target.isDefined) {
      val files = oxygenConf.oxygen.DISCOVERY_ORCHESTRATION.target.get.flatMap(path => {
        val dirPath = appContext.config.oxygen.MARKER_HOME + File.separator + path
        val dirPathInProgress = dirPath + File.separator + "inprogress"
        fileUtils.createDirectory(dirPath)
        fileUtils.createDirectory(dirPathInProgress)
        List(dirPath, dirPathInProgress)
      })

      // If there are no markers in a directory then don't try to load from it
      // otherwise spark will raise an error about not being able to infer the
      // json schema.
      val pathsWithFiles = files.filter(f => {
        // Note that it's important that we do not recursively search, otherwise
        // a directory will have a positive file count (even when it doesn't directly
        // contain files) and so will be included for reading, but when it is read,
        // it will fail with the error described above.
        val hasFiles = fileUtils.listFilenames(f, recursive = false).nonEmpty
        if (!hasFiles) logger.info(s"No markers found in ${f}")
        hasFiles
      })

      if (pathsWithFiles.isEmpty) {
        logger.info("No discovered or in progress markers found")
        List.empty[String]
      } else {
        val updates = oxygenIO.read.dataframe(pathsWithFiles, "text")
        val frame: RDD[ProcessUpdate] = updates.toJSON.rdd.map(y => ScalaJacksonUtil.fromJson(y, classOf[ProcessUpdate]).get)
        frame.map(_.location).collect().toList
      }
    }
    else {
      List.empty[String]
    }
  }

}

