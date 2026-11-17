package com.experian.edf.oxygen.acceptance.lookup

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.configuration.configs.LookupElement
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.module.lookup.hashmap.HashMapBasedLookup
import com.experian.edf.oxygen.module.lookup.{LookupFileFormat, LookupFileReadInfo}
import com.experian.edf.oxygen.utils.SparkFileUtils
import org.apache.commons.io.FilenameUtils.{getBaseName, getExtension, getFullPath}
import org.slf4j.LoggerFactory.getLogger

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 3/17/20 at 8:16 AM
 */
class SparkOxygenLookup(appContext: SparkOxygenAppContext) extends HashMapBasedLookup() {
  @transient lazy val logger = getLogger(this.getClass)
  val fileUtils = new SparkFileUtils(appContext)

  override val cache: LookupMapStructure = loadCache(appContext.config.oxygen.getLookups)
  // val fileUtils = new SparkFileUtils(appContext)

  override def get(tableName: String): Array[Array[Any]] = {
    val table = appContext.sparkSession.table(tableName.replace(".", "_")).cache
    val filteredTable = table.collect().map(row => row.toSeq.toArray)
    filteredTable
  }

  override def loadCache(lookups: Map[String, LookupElement]): LookupMapStructure = appContext.sc.broadcast(super.loadCache(lookups)).value

  override def readLookupFile(lookupFileReadInfo: LookupFileReadInfo): Array[Map[String, Any]] = {
    // check if extension is json or other file that uses a character as delimiter
    val df = if (lookupFileReadInfo.format == LookupFileFormat.JSON) {
      val jsonFile = appContext.sparkSession.sparkContext.wholeTextFiles(lookupFileReadInfo.fileName).map(each => each._2.replace("\n", "").trim)
      appContext.sparkSession.read.json(jsonFile)
    } else {
      appContext.sparkSession.read.option("delimiter", lookupFileReadInfo.delimiter.toString).option("header", value = true).csv(lookupFileReadInfo.fileName)
    }
    // Creates Array(Map(field1 -> field1Val, field2 => field2Val, ...))
    // Eg. Array(Map(State -> Alabama, Abbreviation -> AL), Map(State -> Alaska, Abbreviation -> AK),
    // Map(State -> Arizona, Abbreviation -> AZ)...

    try {
      val dataAsArray = df.collect.map(row => Map(df.columns.zip(row.toSeq): _*))
      dataAsArray
    } catch {
      case e: Exception =>
        logger.debug("Function 'readLookupFile' failed due to a problem in file " + lookupFileReadInfo.fileName + " Full Error Message: " + e)
        throw new OxygenException("Error com.experian.edf.oxygen.lookup.SparkOxygenLookup.readLookupFile a Spark library must be updated. ")
    }


  }

  /**
   * Get a lookup file that has highest number
   * For example, if we have three lookup file for state and three versions,
   * state.csv, states.csv.1, states.csv.2
   * Then, we will get "states.csv.2"
   *
   * @param filePath
   * @return file that has highest number
   */
  def getPathOfLatestVersion(filePath: String): String = {
    val lookUpFilename = getBaseName(filePath)
    val pathName = getFullPath(filePath)

    val sameNameDifferentVersion: List[Int] = fileUtils.listFilePathAndName(pathName)
      .filter(each => each._2.contains(lookUpFilename))
      .map(each => each._2)
      .map(each => {
        try {
          val convertedNum = getExtension(each).toInt
          convertedNum
        } catch {
          case e: Exception => {
            logger.debug("File cannot converted to Int " + e)
            0
          }
        }
      })

    val highestNum = sameNameDifferentVersion.reduceRight((x, y) => if (x > y) x else y)

    val highestNumFileName = if (highestNum != 0) filePath + "." + highestNum
    else filePath

    highestNumFileName
  }


}
