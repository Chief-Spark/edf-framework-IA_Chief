package com.experian.edf.oxygen.consistencycheck.engines

import com.experian.edf.oxygen.consistencycheck.engines.converters.ISDataFrameConverters
import com.experian.edf.oxygen.consistencycheck.engines.io.utils.OxygenIOUtils
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingLogging
import com.experian.edf.oxygen.consistencycheck.standalone.log.ConsistencyLogEvent
import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedConsistencyInfo}
import com.experian.edf.oxygen.consistencycheck.domain.{HeterogeneousCollection, HeterogeneousWritableCollection}
import com.experian.edf.oxygen.engine.spark.converters.DataFrameConverters
//import com.experian.edf.oxygen.io.utils.OxygenIOUtils
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.utils.{FileUtils, ScalaJacksonUtil}
import com.google.gson.JsonObject
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.ScalaReflection
import org.apache.spark.sql.functions.{collect_list, expr, flatten}
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, DataFrameReader, SparkSession}
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory.getLogger

import java.io.File

object SynthesisIOUtils {
  private[oxygen] def makeConsistencyPath(timestamp: String, oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + timestamp + "-consistency-info"
  }

  private[oxygen] def makeConsistencyPathDelta(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + "consistency-info-delta"
  }

  private[oxygen] def makeConsistencyPathNew(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + "consistency-info-new"
  }

  private[oxygen] def makeConsistencyPathDeltaAccountLevel(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + "consistency-info-account-level-delta"
  }

  private[oxygen] def makeConsistencyPathUnchangedAccountLevel(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + "consistency-info-account-level-unchanged"
  }

  private[oxygen] def makeConsistencyPathChanged(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + "consistency-info-changed"
  }

  private[oxygen] def makeConsistencyPathUnchanged(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    consistencyCheckedDir + File.separator + "consistency-info-unchanged"
  }

  def makeSynthesizedPath(timestamp: String, oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir // oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + timestamp
  }

  def makeSynthesizedPathDelta(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir // oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + "synthesized-delta"
  }

  def makeSynthesizedPathNew(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir // oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + "synthesized-new"
  }

  def makeSynthesizedPathDeltaAccountLevel(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir + "-account-level"// oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + "synthesized-delta"
  }

  def makeSynthesizedPathUnchangedAccountLevel(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir + "-account-level" // oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + "synthesized-unchanged"
  }

  def makeSynthesizedPathChanged(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir // oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + "synthesized-changed"
  }

  def makeSynthesizedPathUnchanged(oxygenConf: OxygenConfig): String = {
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir // oxygenConf.oxygen.CONSISTENCY_SYNTHESIZED_DIRECTORY
    consistencyCheckedDir + File.separator + "synthesized-unchanged"
  }

  def getStorageFormat(oxygenConf: OxygenConfig): String = oxygenConf.oxygen.FILE_STORAGE_FORMAT

  def getWriteFormat(oxygenConf: OxygenConfig): String =
    oxygenConf.oxygen.stage.consistency.get.readWriteFormat.get.output.getOrElse(getStorageFormat(oxygenConf))
   // oxygenConf.oxygen.SYNTHESIS_READ_WRITE_FORMAT.output.getOrElse(getStorageFormat(oxygenConf))

  private[consistencycheck] def saveSynthesizedEventsAsTSV(
    oxygenIO: OxygenIOUtils,
    fileUtils: FileUtils,
    tsvDir: String,
    timestamp: String,
    synthesizedEventsWithPinRdd: RDD[(Long, List[SyntheticEvent])]
  ) = {
    fileUtils.deleteFileOrFolder(tsvDir)
    fileUtils.touch(tsvDir, true)
    val synthesizedTSVFileName = tsvDir + File.separator + timestamp
    val syntheticEventsAsTSV = synthesizedEventsWithPinRdd.map(x => {
      s"${x._1}\t${ScalaJacksonUtil.toJson(x._2).get}"
    })
    oxygenIO.write.asTextFile(syntheticEventsAsTSV, synthesizedTSVFileName)
  }
}

/**
 *
 * @author annapurna - C22603A
 *         Created on 3/23/20 at 1:36 PM
 */
class SynthesisIOUtils(fileUtils: FileUtils, appContext: SparkOxygenAppContext) extends Serializable {
  val oxygenConf: OxygenConfig = appContext.config
  val sparkSession: SparkSession = appContext.sparkSession
  val storageFormat: String = SynthesisIOUtils.getStorageFormat(oxygenConf)
  val readFormat: String = oxygenConf.oxygen.stage.consistency.get.readWriteFormat.get.input.getOrElse(storageFormat)
  val writeFormat: String = SynthesisIOUtils.getWriteFormat(oxygenConf)
  val synthesisOutputAsTSV: Boolean = oxygenConf.oxygen.stage.consistency.get.synthesisOutputAsTSV
  val synthesisOutputAsTSVDir: String = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedTsvDir
  val sendConsistencyInfoToKafka: Boolean = oxygenConf.oxygen.stage.consistency.get.sendConsistencyInfoToKafka
  val saveConsistencyToFileEnabled: Boolean = oxygenConf.oxygen.stage.consistency.get.saveConsistencyInfoToFile
  val consistencyTopic: String = oxygenConf.oxygen.getKafkaLoggerTopic.getOrElse("consistencyTopic", "")
  val puTopic = oxygenConf.oxygen.getKafkaLoggerTopic.get("puTopic").getOrElse("")

  val kafkaMsg = new KafkaMsg(oxygenConf)

  val ioUtils: OxygenIOUtils = OxygenIOUtils(sparkSession)


  @transient private lazy val logger = getLogger(this.getClass)


  private[consistencycheck] def retrieveHCData: RDD[HeterogeneousCollection] = {
    // Read all files from curated folder
    val pinningFolder = oxygenConf.oxygen.PINNED_DIRECTORY
    val pinnedFile = fileUtils.listDirectories(pinningFolder, true).filter(eachFile => eachFile.endsWith("-pin-hc"))

    val hcFile = if (pinnedFile.length >= 1) {
      if (pinnedFile.length > 1) {
        logger.error("More than one Pinned files. Picking Last file")
      }
      pinnedFile.last
    } else {
      logger.error("No pinned file found. Returning empty rdd")
      return sparkSession.sparkContext.emptyRDD[HeterogeneousCollection]
    }

    ioUtils.read.heterogeneousCollection(hcFile, readFormat)
  }

  private[consistencycheck] def retrieveHCDataDf: DataFrame = {
    // Read all files from curated folder
    val pinningFolder = oxygenConf.oxygen.PINNED_DIRECTORY
    val pinnedFile = fileUtils.listDirectories(pinningFolder, true).filter(eachFile => eachFile.endsWith("-pin-hc"))
    val hcFile = if (pinnedFile.length >= 1) {
      if (pinnedFile.length > 1) {
        logger.error("More than one Pinned files. Picking Last file")
      }
      pinnedFile.last
    } else {
      logger.error("No pinned file found. Returning empty rdd")
      return sparkSession.emptyDataFrame
    }
    val schema: Option[StructType] = Option(ScalaReflection.schemaFor[HeterogeneousWritableCollection].dataType.asInstanceOf[StructType])
    readFileSchema(hcFile, readFormat, schema)
  }

  private[consistencycheck] def retrieveHCDataDfDelta: DataFrame = {
    // Read all files from curated folder
    val hcDeltaDir = oxygenConf.oxygen.PINNED_DIRECTORY.stripSuffix(File.separator).split(File.separator).dropRight(1).mkString(File.separator) + File.separator + "hc-delta" + File.separator + "detected" + File.separator
    val hcDeltaFiles = fileUtils.listDirectories(hcDeltaDir).filter(v => v.contains("new") || v.contains("changed"))
    val schema: Option[StructType] = Option(ScalaReflection.schemaFor[HeterogeneousWritableCollection].dataType.asInstanceOf[StructType])
    readFilesSchema(hcDeltaFiles, readFormat, schema)
  }

  private[consistencycheck] def retrieveHCDataDfNew: DataFrame = {
    // Read all files from curated folder
    val hcDeltaDir = oxygenConf.oxygen.PINNED_DIRECTORY.stripSuffix(File.separator).split(File.separator).dropRight(1).mkString(File.separator) + File.separator + "hc-delta" + File.separator + "detected" + File.separator
    val hcDeltaFiles = fileUtils.listDirectories(hcDeltaDir).filter(v => v.contains("new"))
    val schema: Option[StructType] = Option(ScalaReflection.schemaFor[HeterogeneousWritableCollection].dataType.asInstanceOf[StructType])
    readFilesSchema(hcDeltaFiles, readFormat, schema)
  }

  private[consistencycheck] def retrieveHCDataDfDeltaAccountLevel: DataFrame = {
    // Read all files from curated folder
    val hcDeltaDir = oxygenConf.oxygen.PINNED_DIRECTORY.stripSuffix(File.separator).split(File.separator).dropRight(1).mkString(File.separator) + File.separator + "hc-delta" + File.separator + "detected-account-level" + File.separator
    val hcDeltaFiles = fileUtils.listDirectories(hcDeltaDir).filter(v => (!v.contains("unchanged")) && ( v.contains("new") || v.contains("changed") ) )
    val schema: Option[StructType] = Option(ScalaReflection.schemaFor[HeterogeneousWritableCollection].dataType.asInstanceOf[StructType])
    readFilesSchema(hcDeltaFiles, readFormat, schema)
  }

  def readFile(filename: String, readFormat: String = "avro"): DataFrame = {
    readFormat match {
      case "avro" => {
        val wildcardSymbols = File.separator + "*" + File.separator + "*"
        if (filename.endsWith(wildcardSymbols)) {
          val cleanPath = filename.stripSuffix(wildcardSymbols)
          val files = fileUtils.listDirectories(cleanPath).filter(!_.contains(".DS_Store"))
          if (files.size <= 1) {
            appContext.sparkSession.read.format("avro").option("encoding", "UTF-8").load(filename)
          } else {
            val validFilesJsonDf = files
              .filter(file => fileUtils.listFilenames(file).filter(v => (!v.contains(".DS_Store")) && (!v.contains("_SUCCESS"))).size > 0)
              .map(v => appContext.sparkSession.read.format("avro").option("encoding", "UTF-8").load(v).toJSON)
              .reduceLeft(_.union(_))
            appContext.sparkSession.read.option("encoding", "UTF-8").json(validFilesJsonDf)
          }
        } else {
          appContext.sparkSession.read.format("avro").option("encoding", "UTF-8").load(filename)
        }
      }
      case "parquet" => sparkSession.read.option("encoding", "UTF-8").option("mergeSchema", "true").parquet(filename)
      case "text" => sparkSession.read.option("encoding", "UTF-8").json(filename)
      case _ => throw new UnsupportedOperationException
    }
  }

  def getAndSaveUnchanged(checkedUnchangedDir: String, synthesizedUnchangedDir: String, checkedReferenceDir: String, synthesizedReferenceDir: String, deletedPath: String, changedPath: String): Unit = {

    val deletedDf = if (fileUtils.exists(deletedPath)) readFile(deletedPath, readFormat) else sparkSession.emptyDataFrame
    val changedDf = if (fileUtils.exists(changedPath)) readFile(changedPath, readFormat).select("pin") else sparkSession.emptyDataFrame
    val deletedAndChangedDf = if (!deletedDf.isEmpty) {
      if (!changedDf.isEmpty) deletedDf.union(changedDf) else deletedDf
    } else {
      if (!changedDf.isEmpty) changedDf else sparkSession.emptyDataFrame
    }
    deletedAndChangedDf.persist()

    if (saveConsistencyToFileEnabled) {
      val checkColsHead = "pin"
      val isEmptyReferenceCheckedDf = if (fileUtils.exists(checkedReferenceDir.stripSuffix("/*/*"))) {
        val filenames = fileUtils.listFilenames(checkedReferenceDir.stripSuffix("/*/*")).filter(!_.contains("DS_Store"))
        if (filenames.size <= 0) true else {
          if (filenames(0).split(File.separator).last != checkedReferenceDir.stripSuffix("/*/*").split(File.separator).last) false else true
        }
      } else true
      val referenceCheckedDf = if (!isEmptyReferenceCheckedDf) readFile(checkedReferenceDir, "avro") else sparkSession.emptyDataFrame

      if (!referenceCheckedDf.isEmpty) {
        val checkCols = referenceCheckedDf.columns
        val unchangedCheckedDf = if (!deletedAndChangedDf.isEmpty) {
          referenceCheckedDf.join(deletedAndChangedDf, Seq(checkColsHead), "left_anti").select(checkCols.head, checkCols.tail: _*)
        } else {
          referenceCheckedDf
        }

        appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
        unchangedCheckedDf.write.format("avro").save(checkedUnchangedDir)
        logger.info("Unchanged consistency info saved to " + checkedUnchangedDir)
        checkAndRemoveEmptyDir(checkedUnchangedDir, "avro")
      }
    }

    val referenceSynthesizedDfRaw = if (fileUtils.exists(synthesizedReferenceDir.stripSuffix("/*/*"))) readFile(synthesizedReferenceDir, readFormat) else sparkSession.emptyDataFrame
    if (referenceSynthesizedDfRaw.isEmpty) {
      throw new Exception("Synthesized in reference generation is empty: " + synthesizedReferenceDir)
    } else {
      val synthesizedCols = referenceSynthesizedDfRaw.columns
      val synthesizedColsHead = "pin"
      val synthesizedColsTail = synthesizedCols.filter(_ != synthesizedColsHead).sorted
      val referenceSynthesizedDf = referenceSynthesizedDfRaw.select(synthesizedColsHead, synthesizedColsTail: _*)
      val unchangedSynthesizedDf = if (!deletedAndChangedDf.isEmpty) {
          referenceSynthesizedDf.join(deletedAndChangedDf, Seq(synthesizedColsHead), "left_anti").select(synthesizedColsHead, synthesizedColsTail: _*)
        } else {
          referenceSynthesizedDf
        }
      ioUtils.write.dataFrame(unchangedSynthesizedDf, synthesizedUnchangedDir, writeFormat)
      checkAndRemoveEmptyDir(synthesizedUnchangedDir, writeFormat)

    }

    deletedAndChangedDf.unpersist()
  }

//  def getReferenceSynthesizedSchema(synthesizedReferenceDir: String): StructType = {
//    val referenceSynthesizedDfRaw = if (fileUtils.exists(synthesizedReferenceDir.stripSuffix("/*/*"))) readFileMergeSchema(synthesizedReferenceDir, readFormat) else sparkSession.emptyDataFrame
//    if (referenceSynthesizedDfRaw.isEmpty) {
//      throw new Exception("Synthesized in reference generation is empty: " + synthesizedReferenceDir)
//    }
//    val referenceSynthesizedSchema = referenceSynthesizedDfRaw.schema
//    referenceSynthesizedSchema
//  }

  def getAndSaveUnchangedAccountLevel(checkedUnchangedDir: String, checkedReferenceDir: String, synthesizedReferenceDir: String, unchangedPath: String): DataFrame = {

    val unchangedDf = if (fileUtils.exists(unchangedPath)) readFile(unchangedPath, readFormat) else sparkSession.emptyDataFrame
    if (!unchangedDf.isEmpty) {
      unchangedDf.persist()
      if (saveConsistencyToFileEnabled) {
        val checkColsHead = "pin"
        val isEmptyReferenceCheckedDf = if (fileUtils.exists(checkedReferenceDir.stripSuffix("/*/*"))) {
          val filenames = fileUtils.listFilenames(checkedReferenceDir.stripSuffix("/*/*")).filter(!_.contains("DS_Store"))
          if (filenames.size <= 0) true else {
            if (filenames(0).split(File.separator).last != checkedReferenceDir.stripSuffix("/*/*").split(File.separator).last) false else true
          }
        } else true
        val referenceCheckedDf = if (!isEmptyReferenceCheckedDf) readFile(checkedReferenceDir, "avro") else sparkSession.emptyDataFrame

        if (!referenceCheckedDf.isEmpty) {
          val checkCols = referenceCheckedDf.columns
          val unchangedCheckedDf = referenceCheckedDf
            .withColumn("subscriber", expr("eventLineage.subscriber as subscriber"))
            .join(unchangedDf, Seq(checkColsHead, "subscriber", "subscriberRelation"), "inner")
            .select(checkCols.head, checkCols.tail: _*)
          appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
          unchangedCheckedDf.write.format("avro").save(checkedUnchangedDir)
          logger.info("Account level unchanged consistency info saved to " + checkedUnchangedDir)
          checkAndRemoveEmptyDir(checkedUnchangedDir, "avro")
        }
      }


      val referenceSynthesizedDfRaw = readFile(synthesizedReferenceDir, readFormat)

      val synthesizedCols = referenceSynthesizedDfRaw.columns
      val synthesizedColsHead = "pin"
      val synthesizedColsTail = synthesizedCols.filter(_ != synthesizedColsHead).sorted
      val referenceSynthesizedDf = referenceSynthesizedDfRaw.select(synthesizedColsHead, synthesizedColsTail: _*)
      val unchangedSynthesizedDf = referenceSynthesizedDf
        .selectExpr(synthesizedColsHead, "explode(synthesizedEvents) as synthesizedEvent")
        .selectExpr(synthesizedColsHead, "synthesizedEvent.subscriber as subscriber", "synthesizedEvent.subscriberRelation as subscriberRelation", "synthesizedEvent")
        .join(unchangedDf, Seq(synthesizedColsHead, "subscriber", "subscriberRelation"), "right")
        .select(synthesizedColsHead, "synthesizedEvent")
        .groupBy(synthesizedColsHead)
        .agg(collect_list("synthesizedEvent").alias("synthesizedEvents"))
        .select(synthesizedColsHead, synthesizedColsTail: _*)
      unchangedSynthesizedDf
    } else sparkSession.emptyDataFrame

  }

  def combineChangedAccountLevel(syntheticEventsWithPinAccountLevelRDD:  RDD[(Long, List[SyntheticEvent])], synthesizedAccountLevelUnchangedDf: DataFrame, synthesizedChangedDir: String): Unit = {
    if (!syntheticEventsWithPinAccountLevelRDD.isEmpty) {
      val synthesizedAccountLevelDeltaDf = getSynthesizedAccountLevelDeltaDf(syntheticEventsWithPinAccountLevelRDD)
      if (!synthesizedAccountLevelUnchangedDf.isEmpty) {
        val changedPinLevelDf = sparkSession.read.option("encoding", "UTF-8").json(synthesizedAccountLevelDeltaDf.toJSON.union(synthesizedAccountLevelUnchangedDf.toJSON))
          .groupBy("pin").agg(flatten(collect_list("synthesizedEvents")).alias("synthesizedEvents"))
        ioUtils.write.dataFrame(changedPinLevelDf, synthesizedChangedDir, writeFormat)
      } else {
        ioUtils.write.dataFrame(synthesizedAccountLevelDeltaDf, synthesizedChangedDir, writeFormat)
      }
    } else {
      if (!synthesizedAccountLevelUnchangedDf.isEmpty) {
        ioUtils.write.dataFrame(synthesizedAccountLevelUnchangedDf, synthesizedChangedDir, writeFormat)
      } else {
        checkAndRemoveEmptyDir(synthesizedChangedDir, writeFormat)
      }
    }
  }


    def checkAndRemoveEmptyDir(dir: String, format: String) = {
    val fileToCheck = dir
    if (fileUtils.exists(fileToCheck)) {
      val isStateEmpty = readFile(fileToCheck, format).isEmpty
      if (isStateEmpty) {
        fileUtils.deleteFileOrFolder(fileToCheck)
        logger.info(s"Output is empty and removed: $fileToCheck")
      } else logger.info(s"Output checked and is not empty: $fileToCheck")
    } else logger.info(s"Output checked and is not empty: $fileToCheck")
  }

  def readFileSchema(filename: String, readFormat: String = "avro", schema: Option[StructType]): DataFrame = {
    //    logger.error("Reading " + filename + " in format " + readFormat)
    val dsReader: DataFrameReader = if (schema.isDefined) sparkSession.read.schema(schema.get) else sparkSession.read
    readFormat match {
      case "avro" => dsReader.format("avro").option("encoding", "UTF-8").load(filename)
      case "parquet" => dsReader.option("encoding", "UTF-8").parquet(filename)
      case "text" => dsReader.option("encoding", "UTF-8").json(filename)
      case _ => throw new UnsupportedOperationException
    }
  }

  def readFilesSchema(filenames: List[String], readFormat: String = "avro", schema: Option[StructType]): DataFrame = {
    //    logger.error("Reading " + filename + " in format " + readFormat)
    val dsReader: DataFrameReader = if (schema.isDefined) sparkSession.read.schema(schema.get) else sparkSession.read
    readFormat match {
      case "avro" => dsReader.format("avro").option("encoding", "UTF-8").load(filenames: _*)
      case "parquet" => dsReader.option("encoding", "UTF-8").parquet(filenames: _*)
      case "text" => dsReader.option("encoding", "UTF-8").json(filenames: _*)
      case _ => throw new UnsupportedOperationException
    }
  }


  def saveHCOverSSRThreshold(df: DataFrame, fileToWrite: String, writeFormat:String, writeMode: String = "overwrite") = {
//    logger.info("Writing " + fileToWrite + " in format " + writeFormat)
    writeFormat match {
      case "avro" => df.write.partitionBy("pin", "ssrHash").mode(writeMode).format("avro").option("encoding", "UTF-8").save(fileToWrite)
      case "parquet" => df.write.partitionBy("pin", "ssrHash").mode(writeMode).option("encoding", "UTF-8").parquet(fileToWrite)
      case "text" => df.write.partitionBy("pin", "ssrHash").mode(writeMode).option("encoding", "UTF-8").json(fileToWrite)
      case _ => throw new UnsupportedOperationException
    }
    logger.debug(s"HC pins over SSR threshold written to: $fileToWrite")
  }

  private[consistencycheck] def saveSynthesisOutput(allInconsistenciesRDD: RDD[TaggedConsistencyInfo], syntheticEventsWithPinRDD: RDD[(Long, List[SyntheticEvent])]): Unit = {
    val timestamp = System.currentTimeMillis().toString
    val synthesizedEventsDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir
    archiveAndCleanConsistencyDir(timestamp)
    saveConsistencyInfo(timestamp, allInconsistenciesRDD)
    if (synthesisOutputAsTSV) {
      syntheticEventsWithPinRDD.persist(StorageLevel.DISK_ONLY)
      SynthesisIOUtils.saveSynthesizedEventsAsTSV(
        ioUtils,
        fileUtils,
        synthesisOutputAsTSVDir,
        timestamp,
        syntheticEventsWithPinRDD
      )
    }
    saveSynthesizedEvents(timestamp, synthesizedEventsDir, syntheticEventsWithPinRDD, writeFormat)
  }

  private[consistencycheck] def saveSynthesisOutputDelta(allInconsistenciesRDD: RDD[TaggedConsistencyInfo], syntheticEventsWithPinRDD: RDD[(Long, List[SyntheticEvent])]): Unit = {
    val timestamp = System.currentTimeMillis().toString
    val consistencyFilename = SynthesisIOUtils.makeConsistencyPathDelta(oxygenConf)
    val synthesizedEventsDir = SynthesisIOUtils.makeSynthesizedPathDelta(oxygenConf)

    archiveAndCleanConsistencyDir(timestamp)
    saveConsistencyInfoDelta(timestamp, allInconsistenciesRDD, consistencyFilename)
//    if (synthesisOutputAsTSV) {
//      syntheticEventsWithPinRDD.persist(StorageLevel.DISK_ONLY)
//      SynthesisIOUtils.saveSynthesizedEventsAsTSV(
//        ioUtils,
//        fileUtils,
//        synthesisOutputAsTSVDir,
//        timestamp,
//        syntheticEventsWithPinRDD
//      )
//    }
    saveSynthesizedEventsDelta(timestamp, synthesizedEventsDir, syntheticEventsWithPinRDD, writeFormat)
  }

  private[consistencycheck] def saveSynthesisOutputNew(allInconsistenciesRDD: RDD[TaggedConsistencyInfo], syntheticEventsWithPinRDD: RDD[(Long, List[SyntheticEvent])]): Unit = {
    val timestamp = System.currentTimeMillis().toString
    val consistencyFilename = SynthesisIOUtils.makeConsistencyPathNew(oxygenConf)
    val synthesizedEventsDir = SynthesisIOUtils.makeSynthesizedPathNew(oxygenConf)

    archiveAndCleanConsistencyDir(timestamp)
    saveConsistencyInfoDelta(timestamp, allInconsistenciesRDD, consistencyFilename)
    //    if (synthesisOutputAsTSV) {
    //      syntheticEventsWithPinRDD.persist(StorageLevel.DISK_ONLY)
    //      SynthesisIOUtils.saveSynthesizedEventsAsTSV(
    //        ioUtils,
    //        fileUtils,
    //        synthesisOutputAsTSVDir,
    //        timestamp,
    //        syntheticEventsWithPinRDD
    //      )
    //    }
    saveSynthesizedEventsDelta(timestamp, synthesizedEventsDir, syntheticEventsWithPinRDD, writeFormat)
  }

  private[consistencycheck] def saveCheckedOutputDeltaAccountLevel(allInconsistenciesRDD: RDD[TaggedConsistencyInfo]): Unit = {
    val timestamp = System.currentTimeMillis().toString
    val consistencyFilename = SynthesisIOUtils.makeConsistencyPathDeltaAccountLevel(oxygenConf)
    saveConsistencyInfoDelta(timestamp, allInconsistenciesRDD, consistencyFilename)
  }

  private[consistencycheck] def getSynthesizedAccountLevelDeltaDf(syntheticEventsWithPinRDD: RDD[(Long, List[SyntheticEvent])]): DataFrame = {
    ISDataFrameConverters.forSyntheticEvent(appContext.sparkSession, syntheticEventsWithPinRDD)
  }

  private[oxygen] def archiveAndCleanConsistencyDir(timestamp: String) = {
    val archiveDir = oxygenConf.oxygen.stage.consistency.get.paths.archivedDir
    val consistencyCheckedDir = oxygenConf.oxygen.stage.consistency.get.paths.checkedDir
    val consistencySynthesizedDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir
    val consistencySynthesizedTSVDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedTsvDir
    // TODO: check this
    fileUtils.moveFileToDir(consistencyCheckedDir, archiveDir + File.separator + "archived-on-" + timestamp)
    fileUtils.moveFileToDir(consistencySynthesizedDir, archiveDir + File.separator + "archived-on-" + timestamp)
    fileUtils.moveFileToDir(consistencySynthesizedTSVDir, archiveDir + File.separator + "archived-on-" + timestamp)
    fileUtils.deleteFileOrFolder(consistencyCheckedDir)
    fileUtils.deleteFileOrFolder(consistencySynthesizedDir)
    fileUtils.deleteFileOrFolder(consistencySynthesizedTSVDir)
    fileUtils.touch(consistencyCheckedDir, true)
    fileUtils.touch(consistencySynthesizedDir, true)
    fileUtils.touch(consistencySynthesizedTSVDir, true)
  }

  private[consistencycheck] def saveConsistencyInfo(timestamp: String, allInconsistenciesRdd: RDD[TaggedConsistencyInfo]): Unit = {
    // TODO: the saving of file should be under one transaction
    logger.debug(s"saveConsistencyInfo started")
    if (allInconsistenciesRdd != null) {
      // Send data to Kafka
      if (oxygenConf.oxygen.KAFKA_ENABLED && sendConsistencyInfoToKafka) {
        allInconsistenciesRdd.foreach { each =>
          val addedAdditionalLogInfo = new ConsistencyLogEvent(each.pin, each.lineage, each.subscriberRelation, each.eventLineage, each.checkName, each.eventId, each.consistencyInfo, each.isFiltered,
            timestamp, "consistencyInfo")
          logger.debug(s"consistency info message is \n {}", addedAdditionalLogInfo.toString)
          kafkaMsg.sendRecordWithProperties(appContext.config, consistencyTopic, addedAdditionalLogInfo.toString, each.lineage.datasetId)
        }
        logger.info("Consistency info sent to Kafka finished")

      }
      // Save to Hdfs
      if (saveConsistencyToFileEnabled) {
        val consistencyFilename = SynthesisIOUtils.makeConsistencyPath(timestamp, oxygenConf)
        val allInconsistenciesAsDF = appContext.sparkSession.read.json(allInconsistenciesRdd.map(x => ScalaJacksonUtil.toJson(x).get))
        appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
        if (!allInconsistenciesAsDF.rdd.isEmpty()) allInconsistenciesAsDF.write.format("avro").save(consistencyFilename)
        logger.info("Consistency info saved to " + consistencyFilename)
      }
    }
  }

  private[consistencycheck] def saveConsistencyInfoDelta(timestamp: String, allInconsistenciesRdd: RDD[TaggedConsistencyInfo], consistencyFilename: String): Unit = {
    // TODO: the saving of file should be under one transaction
    logger.debug(s"saveConsistencyInfo started")
    if (allInconsistenciesRdd != null) {
      logger.error("Processing allInconsistenciesRdd...")
      // Send data to Kafka
      // TODO: only sending delta checked; need to send unchanged, too.
      if (oxygenConf.oxygen.KAFKA_ENABLED && sendConsistencyInfoToKafka) {
        allInconsistenciesRdd.foreach { each =>
          val addedAdditionalLogInfo = new ConsistencyLogEvent(each.pin, each.lineage, each.subscriberRelation, each.eventLineage, each.checkName, each.eventId, each.consistencyInfo, each.isFiltered,
            timestamp, "consistencyInfo")
          logger.debug(s"consistency info message is \n {}", addedAdditionalLogInfo.toString)
          kafkaMsg.sendRecordWithProperties(appContext.config, consistencyTopic, addedAdditionalLogInfo.toString, each.lineage.datasetId)
        }
        logger.info("Consistency info sent to Kafka finished")

      }
      // Save to Hdfs
      if (saveConsistencyToFileEnabled) {
        if (fileUtils.exists(consistencyFilename)) fileUtils.deleteFileOrFolder(consistencyFilename)
        val allInconsistenciesAsDF = appContext.sparkSession.read.json(allInconsistenciesRdd.map(x => ScalaJacksonUtil.toJson(x).get))
        appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
        if (!allInconsistenciesAsDF.rdd.isEmpty()) allInconsistenciesAsDF.write.format("avro").save(consistencyFilename)
        logger.error("Delta consistency info saved to " + consistencyFilename)
      }
    }
  }

  private[consistencycheck] def saveSynthesizedEvents(
    timestamp: String,
    synthesizedEventsDir: String,
    synthesizedEventsWithPinRdd: RDD[(Long, List[SyntheticEvent])],
    writeFormat: String
  ) = {
    // TODO: the saving of file should be under one transaction
    if (synthesizedEventsWithPinRdd != null) {
      val synthesizedEventFilename = SynthesisIOUtils.makeSynthesizedPath(timestamp, oxygenConf)
      val writableDF = ISDataFrameConverters.forSyntheticEvent(
        appContext.sparkSession, synthesizedEventsWithPinRdd
      )
      ioUtils.write.dataFrame(writableDF, synthesizedEventFilename, writeFormat)
    }
  }

    def sendSubscriberMigrationInfo(listEvents : List[RelationshipMigrationTrackingLogging] ): Unit = {
      val Cc =  oxygenConf.oxygen.stage.consistency.get
    // TODO: the saving of file should be under one transaction
    logger.debug(s"sendSubscriberMigration data Kafka Enabled = [" + oxygenConf.oxygen.KAFKA_ENABLED.toString+" ] subscriberMigrationLoggingEnabled [" + Cc.migrationLoggingEnabled + " ] ")

      // Send data to Kafka
      if (oxygenConf.oxygen.KAFKA_ENABLED && Cc.migrationLoggingEnabled ) {

        listEvents.foreach(x =>  {

          val jsonStringAsObject = new JsonObject()
          jsonStringAsObject.addProperty("Subscriber",x.subscriber)
          jsonStringAsObject.addProperty("SubscriberRelationship",x.subscriberRelation)
          jsonStringAsObject.addProperty("DestinationSubscriber",x.destinationSubscriber)
          jsonStringAsObject.addProperty("DestinationSubscriberRelation",x.destinationSubscriberRelation)
          jsonStringAsObject.addProperty("AsOfDate",x.asOfDate)
          jsonStringAsObject.addProperty("messageType","subscriberMigrationAccountInfo")
          val innerObject = new JsonObject()
          innerObject.addProperty("subscriberMigrationAccount",jsonStringAsObject.toString)
          kafkaMsg.sendRecordWithProperties(appContext.config, puTopic,jsonStringAsObject.toString, "" )

          logger.info("subscriberMigrationAccountInfo data send to Kafka finished")

        })
      }
  }

  private[consistencycheck] def saveSynthesizedEventsDelta(
                                                       timestamp: String,
                                                       synthesizedEventsDir: String,
                                                       synthesizedEventsWithPinRdd: RDD[(Long, List[SyntheticEvent])],
                                                       writeFormat: String
                                                     ) = {
    // TODO: the saving of file should be under one transaction
    if (!synthesizedEventsWithPinRdd.isEmpty()) {
      if (fileUtils.exists(synthesizedEventsDir)) fileUtils.deleteFileOrFolder(synthesizedEventsDir)
      val writableDF = ISDataFrameConverters.forSyntheticEvent(
        appContext.sparkSession, synthesizedEventsWithPinRdd
      )
      ioUtils.write.dataFrame(writableDF, synthesizedEventsDir, writeFormat)
    }
  }



}
