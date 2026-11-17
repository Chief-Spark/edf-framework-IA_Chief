package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap
import com.experian.edf.oxygen.global.utils.IdGenerator
import com.experian.edf.oxygen.utils.FileUtils
import org.apache.commons.io.FilenameUtils.getName
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}
import org.slf4j.LoggerFactory.getLogger

import java.io.File

class DataExtractorIOUtils(fileUtils: FileUtils, appContext: SparkOxygenAppContext) extends Serializable {
  @transient private lazy val logger = getLogger(this.getClass)
  private val oxygenConf: OxygenConfig = appContext.config
  private val sparkSession: SparkSession = appContext.sparkSession
  private val readFormat: String = oxygenConf.oxygen.stage.dataExtractor.get.readWriteFormat.get.input.get
  private val writeFormat: String = oxygenConf.oxygen.stage.dataExtractor.get.readWriteFormat.get.output.get
  private val tempDirectory = appContext.config.oxygen.TEMP_HOME + File.separator + "dataExtractor"
  private val oxygenIOUtils = OxygenIOUtils(sparkSession)
  private val dataExtractorConfig = appContext.config.oxygen.stage.dataExtractor.get


  def readCuratedEvents: RDD[TaggedCuratedEventWithMap] = {
    val curatedFile = oxygenConf.oxygen.stage.curation.get.paths.curatedDir + File.separator + "*" + File.separator + "*"
    readCuratedEvents(curatedFile, readFormat)
  }

  def readCuratedEvents(readFormat: String): RDD[TaggedCuratedEventWithMap] = {
    val curatedFile = oxygenConf.oxygen.stage.curation.get.paths.curatedDir + File.separator + "*" + File.separator + "*"
    readCuratedEvents(curatedFile, readFormat)
  }

  def readCuratedEvents(curatedFile: String, readFormat: String): RDD[TaggedCuratedEventWithMap] = {
    logger.info("Curated File : {}", curatedFile)
    oxygenIOUtils.read.taggedCuratedEventsFromWritable(curatedFile, readFormat)
  }

  def readDataExtractorData(extractionFile: String, readFormat: String): RDD[TaggedCuratedEventWithMap] = {
    logger.info("DataExtractor File : {}", extractionFile)
    oxygenIOUtils.read.taggedCuratedEventsFromWritable(extractionFile, readFormat)
  }


  def saveDataExtractorData(data: Dataset[TaggedCuratedEventWithMap], target: String, archive: Boolean, partitionedBy: Option[String] = None): Boolean = {
    val tempLocation = tempDirectory + File.separator + getName(target) + File.separator + IdGenerator.getUniqueId()
    val tempWriteSuccess = oxygenIOUtils.write.dataExtractorTCE(data, tempLocation, writeFormat, partitionedBy)
    if (tempWriteSuccess) {
      if (archive)
        archiveDataExtractorDir()
      return fileUtils.moveFile(tempLocation, target)
    }
    tempWriteSuccess
  }

  private[oxygen] def archiveDataExtractorDir(): Boolean = {
    val dataExtractorExtractDir = dataExtractorConfig.paths.dataExtractorExtractDir
    val dataExtractorArchivedDir = dataExtractorConfig.paths.dataExtractorArchivedDir
    fileUtils.moveFile(dataExtractorExtractDir, dataExtractorArchivedDir + File.separator + "dataExtractorExtract-archived-on-" + System.currentTimeMillis())
    fileUtils.touch(dataExtractorExtractDir, isDir = true)
  }
}
