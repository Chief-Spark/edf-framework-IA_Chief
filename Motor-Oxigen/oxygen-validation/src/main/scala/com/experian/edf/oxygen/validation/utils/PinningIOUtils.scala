package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap
import com.experian.edf.oxygen.utils.FileUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory.getLogger

import scala.reflect.io.File

class PinningIOUtils(fileUtils: FileUtils, appContext: SparkOxygenAppContext) extends Serializable {
  @transient private lazy val logger = getLogger(this.getClass)
  private val oxygenConf: OxygenConfig = appContext.config
  private val sparkSession: SparkSession = appContext.sparkSession
  private val readFormat: String = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.input.get
  private val writeFormat: String = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.output.get
  private val oxygenIO = OxygenIOUtils(sparkSession)

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
    oxygenIO.read.taggedCuratedEventsFromWritable(curatedFile, readFormat)
  }

}