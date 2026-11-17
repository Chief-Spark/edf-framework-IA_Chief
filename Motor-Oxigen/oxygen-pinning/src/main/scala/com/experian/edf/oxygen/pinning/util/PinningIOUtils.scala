package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{PinnedEventWithMap, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.utils.FileUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Dataset, SparkSession}
import org.slf4j.LoggerFactory.getLogger

import scala.reflect.io.File
import scala.util.{Failure, Success, Try}

class PinningIOUtils(fileUtils: FileUtils, appContext: SparkOxygenAppContext) extends Serializable {
  @transient private lazy val logger = getLogger(this.getClass)
  private val oxygenConf: OxygenConfig = appContext.config
  private val sparkSession: SparkSession = appContext.sparkSession
  private val readFormat: String = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.input.get
  private val writeFormat: String = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.output.get
  private val oxygenIO = OxygenIOUtils(sparkSession)
  private val inputRefDir = oxygenConf.oxygen.INPUT_REF_DIR

  def readCuratedEvents: RDD[TaggedCuratedEventWithMap] = {
    val events = if(inputRefDir.isDefined && inputRefDir.get!=""){
        readCuratedEvents(inputRefDir.get, oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.input.get)
    } else {
        val curatedFile = oxygenConf.oxygen.stage.curation.get.paths.curatedDir + File.separator + "*" + File.separator + "*"
        readCuratedEvents(curatedFile, readFormat)
    }
    events
  }

  def readCuratedStockEvents: RDD[TaggedCuratedEventWithMap] = {
    val curatedFile = oxygenConf.oxygen.stage.curation.get.paths.curatedDir + File.separator + "stock" + File.separator + "*"
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

  def readKeepTogetherRecords: RDD[String] = {
    val files = fileUtils.listFilenames(oxygenConf.oxygen.KEEP_TOGETHER_DIRECTORY)
    if (files.nonEmpty) {
      Try(readKeepTogetherRecords(files)) match {
        case Success(data) => data
        case Failure(exception) =>
          logger.info(s"Cannot Read Keep together Files : Total Files found: ${files.length}: ${exception.getMessage}", exception)
          null
      }
    } else {
      logger.info(s"No Keep together file Found")
      null
    }
  }

  /**
   * Save pinned events.
   * @param pinnedEvents RDD with pinned events.
   * @param filename File name to save to.
   * @param sparkSession Spark session to use.
   */
  private[oxygen] def saveStampedEvents(pinnedEvents: RDD[PinnedEventWithMap], filename: String, sparkSession: SparkSession): Unit = {
    oxygenIO.write.pinnedEventWithMap(pinnedEvents, filename, writeFormat)
  }

  def readKeepTogetherRecords(ktFile: List[String]): RDD[String] = oxygenIO.read.dataframe(ktFile, readFormat).toJSON.rdd

  def saveCelebrityEvent(data: Dataset[TaggedCuratedEventWithMap], location: String): Unit = {
    oxygenIO.write.taggedCuratedEventsWithMap(data, location, "avro", None)
    //    oxygenIOUtils.write.taggedCuratedEventsWithMap(data, location, "json", None)
  }
}