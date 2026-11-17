package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.global.utils.IdGenerator
import com.experian.edf.oxygen.io.OxygenIO
import com.experian.edf.oxygen.utils.{FileUtils, ScalaJacksonUtil}
import org.apache.commons.io.FilenameUtils._
import org.apache.spark.sql.{Dataset, SparkSession}
import org.slf4j.LoggerFactory.getLogger

import java.io.File

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/30/19 at 8:42 PM
 */
class ValidationIOUtils(appContext: SparkOxygenAppContext, fileUtils: FileUtils) extends Serializable {
  @transient lazy private val logger = getLogger(this.getClass)

  private val spark: SparkSession = appContext.sparkSession
  private val tempDirectory = appContext.config.oxygen.TEMP_HOME + File.separator + "validated"

  private val readFormat: String = appContext.config.oxygen.VALIDATION_READ_WRITE_FORMAT.input.get
  private val writeFormat = appContext.config.oxygen.VALIDATION_READ_WRITE_FORMAT.output.get
  private val oxygenIOUtils = OxygenIOUtils(spark)
  private val oxygenIO = new OxygenIO(spark)

  import spark.implicits._

  def readTaggedValidatedEvent(fileUtils: FileUtils, locations: List[String]): Dataset[TaggedValidatedEvent] = {
    val rdd = oxygenIO.read.dataframe(locations, readFormat).toJSON
    rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[TaggedValidatedEvent]).get)
  }

  def saveValidatedData(data: Dataset[TaggedValidatedEvent], target: String, archive: Boolean): Boolean = {
    val tempLocation = tempDirectory + File.separator + getName(target) + File.separator + IdGenerator.getUniqueId()
    val tempWriteSuccess = oxygenIOUtils.write.taggedValidatedEvents(data, tempLocation, writeFormat, None)
    if (tempWriteSuccess) {
      return fileUtils.moveFile(tempLocation, target)
    }
    tempWriteSuccess
  }

}
