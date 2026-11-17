package com.experian.edf.oxygen.curation.engines

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.global.utils.IdGenerator
import com.experian.edf.oxygen.io.OxygenIO
import com.experian.edf.oxygen.curation.engines.io.utils.OxygenIOUtils
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.utils.{FileUtils, ScalaJacksonUtil}
import org.apache.commons.io.FilenameUtils._
import org.apache.spark.sql.{Dataset, SparkSession}
import org.slf4j.LoggerFactory.getLogger

import java.io.File
import scala.util.{Success, Try}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/30/19 at 8:42 PM
 */
class CuratorIOUtils(appContext: SparkOxygenAppContext, fileUtils: FileUtils) extends Serializable {
  @transient lazy private val logger = getLogger(this.getClass)

  private val spark: SparkSession = appContext.sparkSession
  private val tempDirectory = appContext.config.oxygen.TEMP_HOME + File.separator + "curated"
  private val curationConfig = appContext.config.oxygen.stage.curation.get

  private val readFormat: String = curationConfig.readWriteFormat.get.input.get
  private val writeFormat = curationConfig.readWriteFormat.get.output.get
  private val oxygenIOUtils = OxygenIOUtils(spark)
  private val oxygenIO = new OxygenIO(spark)
  private val numPartitions: Option[Int] = curationConfig.partionSize

  import spark.implicits._


  def readTaggedValidatedEvent(fileUtils: FileUtils, locations: List[String]): Dataset[TaggedValidatedEvent] = {
    val rdd = oxygenIO.read.dataframe(locations, readFormat).toJSON
    rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[TaggedValidatedEvent]).get)
  }

  private[oxygen] def archiveCuratedDir(): Boolean = {
    val curatedDir = curationConfig.paths.curatedDir
    val curatedArchivedDir = curationConfig.paths.archivedDir
    val archiveDirPath = curatedArchivedDir + File.separator + "curated-archived-on-" + System.currentTimeMillis()
    fileUtils.createDirectory(archiveDirPath)
    fileUtils.moveFile(curatedDir, archiveDirPath + File.separator + "curated")
    Try(fileUtils.moveFile(curationConfig.paths.curatedSSRDir, archiveDirPath + File.separator + "curatedSSR")).recoverWith({
      case _: Throwable =>
        logger.info("No curated SSR Directory Found")
        Success()
    })
    fileUtils.touch(curatedDir, isDir = true)
  }

  def saveCuratedData(data: Dataset[TaggedCuratedEventWithMap], target: String, archive: Boolean): Boolean = {
    val tempCurationLocation = tempDirectory + File.separator + getName(target) + File.separator + IdGenerator.getUniqueId() + File.separator + "curated"
    val tempCurationSSRLocation: Option[String] = if (curationConfig.enableSSRWrite)
      Option(tempDirectory + File.separator + getName(target) + File.separator + IdGenerator.getUniqueId() + File.separator + "curatedSSR")
    else None
    val tempWriteSuccess = oxygenIOUtils.write.taggedCuratedEventsWithMap(
      data = data,
      location = tempCurationLocation,
      format = writeFormat,
      partitions = numPartitions,
      ssrLocation = tempCurationSSRLocation
    )
    if (tempWriteSuccess) {
      if (archive)
        archiveCuratedDir()
      val isSSRMoved = if (tempCurationSSRLocation.isDefined) {
        if (!fileUtils.exists(curationConfig.paths.curatedSSRDir)) fileUtils.createDirectory(curationConfig.paths.curatedSSRDir)
        fileUtils.moveFile(tempCurationSSRLocation.get, curationConfig.paths.curatedSSRDir + File.separator + System.nanoTime())
      } else true
      fileUtils.moveFile(tempCurationLocation, target) && isSSRMoved
    } else false
  }

  def saveCelebrityEvent(data: Dataset[TaggedCuratedEventWithMap], location: String): Unit = {
    oxygenIOUtils.write.taggedCuratedEventsWithMap(data, location, "avro", None)
//    oxygenIOUtils.write.taggedCuratedEventsWithMap(data, location, "json", None)
  }
}
