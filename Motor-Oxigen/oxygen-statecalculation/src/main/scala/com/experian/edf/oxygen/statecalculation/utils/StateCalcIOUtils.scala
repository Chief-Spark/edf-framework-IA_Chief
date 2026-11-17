package com.experian.edf.oxygen.statecalculation.utils

import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.dataaccess.hbase.{HbaseConnectionContext, HbaseDataAccess}
import com.experian.edf.oxygen.global.domain.statecalc.SyntheticEvents
import com.experian.edf.oxygen.io.utils.OxygenIOUtils
import com.experian.edf.oxygen.stage.statecalculation.{FoundationModel, XpmModel}
import com.experian.edf.oxygen.statecalc.StateCalcConfig
import com.experian.edf.oxygen.statecalculation.converters.DataFrameConverters
import com.experian.edf.oxygen.statecalculation.engines.StateCalculator
import com.experian.edf.oxygen.utils.{FileUtils, JsonUtilExperimental, ScalaJacksonUtil}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.broadcast
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory.getLogger

import java.io.{File, FileNotFoundException}
import java.time.Instant

object StateCalcIOUtils {

  @transient private lazy val logger = getLogger(this.getClass)

  def makeCalculatedOutputPath(timestamp: String, oxygenConf: OxygenConfig): String = {
    oxygenConf.oxygen.stage.stateCalc.paths.calculatedOutput + File.separator + timestamp
  }

  def makeCalculatedDeltaPath(suffix: String, oxygenConf: OxygenConfig): String = {
    oxygenConf.oxygen.stage.stateCalc.paths.calculatedOutput.stripSuffix(File.separator) + File.separator + "calculated-" + suffix
  }

  def makeCalculatedOutputPathDelta(timestamp: String, oxygenConf: OxygenConfig): String = {
    makeCalculatedDeltaPath("delta", oxygenConf)
  }

  def getWriteFormat(oxygenConf: OxygenConfig): String =
    oxygenConf.oxygen.stage.stateCalc.readWriteFormat.get.output.get

  private[statecalculation] def saveCalculationAsTSV(
    ioUtils: OxygenIOUtils,
    tsvDir: String,
    timestamp: String,
    aggregation: RDD[XpmModel]
  ): Unit = {
    val aggregatedTsvFileOutput = tsvDir + File.separator + timestamp
    // tsv file for aggregation state
    val outputAsTSV: RDD[String] = aggregation.map(each => s"${each.pin}\t${ScalaJacksonUtil.toJsonWithoutOption(each.data)}")
    ioUtils.write.asTextFile(outputAsTSV, aggregatedTsvFileOutput)
    logger.debug("Succeeded in writing XPM to tsv file, now use 'importtsv' to bulk load to hbase")
    logger.debug(s"Calculation state TSV file available at: " + aggregatedTsvFileOutput)
  }
}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 1/7/20 at 11:23 AM
 */
class StateCalcIOUtils(fileUtils: FileUtils, appContext: SparkOxygenAppContext) extends Serializable {
  val oxygenConf: OxygenConfig = appContext.config
  val sparkSession: SparkSession = appContext.sparkSession
  val stateCalcConfig = oxygenConf.oxygen.stage.stateCalc
  val readFormat: String = stateCalcConfig.readWriteFormat.get.input.get
  val writeFormat: String = StateCalcIOUtils.getWriteFormat(oxygenConf)
  val ioUtils = OxygenIOUtils(sparkSession)

  @transient private lazy val logger = getLogger(this.getClass)

  private[oxygen] def archiveAndCleanStateCalcDir(timestamp: String): Boolean = {
    val foundationOutputDir = stateCalcConfig.paths.foundationOutput
    val calculatedOutputDir = stateCalcConfig.paths.calculatedOutput
    val calculatedTsvOutputDir = stateCalcConfig.paths.calculatedTsvOutput
    val archivedTimestampDir = stateCalcConfig.paths.archivedDir + "/archived-on-" + timestamp
    val additionalFoundationOutputDir = stateCalcConfig.paths.additionalFoundationOutput
    fileUtils.touch(archivedTimestampDir, isDir = true)
    fileUtils.moveFileToDir(foundationOutputDir, archivedTimestampDir)
    fileUtils.moveFileToDir(calculatedOutputDir, archivedTimestampDir)
    fileUtils.moveFileToDir(calculatedTsvOutputDir, archivedTimestampDir)
    //fileUtils.touch(additionalFoundationOutputDir, isDir = true)
    if(fileUtils.exists(additionalFoundationOutputDir)) fileUtils.moveFileToDir(additionalFoundationOutputDir, archivedTimestampDir)

    fileUtils.deleteFileOrFolder(foundationOutputDir)
    fileUtils.deleteFileOrFolder(calculatedOutputDir)
    fileUtils.deleteFileOrFolder(calculatedTsvOutputDir)
    fileUtils.deleteFileOrFolder(additionalFoundationOutputDir)

    fileUtils.touch(foundationOutputDir, isDir = true)
    fileUtils.touch(calculatedOutputDir, isDir = true)
    fileUtils.touch(calculatedTsvOutputDir, isDir = true)
    fileUtils.touch(additionalFoundationOutputDir, isDir = true)
  }

  private[statecalculation] def saveFoundationState(foundation: RDD[FoundationModel], timestamp: String): Unit = {
    val foundationOutputDir = stateCalcConfig.paths.foundationOutput
    val foundationFileOutput = foundationOutputDir + File.separator + timestamp

    val foundationState = appContext.sparkSession.read.json(foundation.map(each => {
      ScalaJacksonUtil.toJsonWithoutOption(each.data)
    }))
    saveBasedOnStorageFormat(foundationState,foundationFileOutput,timestamp)
  }

  private[statecalculation] def makeFoundationDeltaPath(suffix: String): String = {
    stateCalcConfig.paths.foundationOutput.stripSuffix(File.separator) + File.separator + "foundation-" + suffix
  }

  private[statecalculation] def saveFoundationStateDelta(foundation: RDD[FoundationModel], timestamp: String): Unit = {
    val foundationFileOutput = makeFoundationDeltaPath("delta")
    val foundationState = appContext.sparkSession.read.json(foundation.map(each => {
      ScalaJacksonUtil.toJsonWithoutOption(each.data)
    }))
    saveBasedOnStorageFormat(foundationState, foundationFileOutput, timestamp)
  }

  private[statecalculation] def saveAdditionalFoundationState(foundation: RDD[FoundationModel], timestamp: String): Unit = {

    val foundationOutputAdditionalDir = stateCalcConfig.paths.additionalFoundationOutput // additionalOutput
    val foundationOutputAdditionalFileOutput = foundationOutputAdditionalDir + File.separator + timestamp

    val foundationStateAdditionalState = appContext.sparkSession.read.json(foundation.map(each => {
      ScalaJacksonUtil.toJsonWithoutOption(each.additionalData)
    }))
    saveBasedOnStorageFormat(foundationStateAdditionalState,foundationOutputAdditionalFileOutput,timestamp)

  }

  private[statecalculation] def makeAdditionalFoundationDeltaPath(suffix: String): String = {
    stateCalcConfig.paths.additionalFoundationOutput.stripSuffix(File.separator) + File.separator + "additionalFoundation-" + suffix
  }

  private[statecalculation] def saveAdditionalFoundationStateDelta(foundation: RDD[FoundationModel], timestamp: String): Unit = {
    val foundationOutputAdditionalFileOutput = makeAdditionalFoundationDeltaPath("delta")

    val foundationStateAdditionalState = appContext.sparkSession.read.json(foundation.map(each => {
      ScalaJacksonUtil.toJsonWithoutOption(each.additionalData)
    }))
    saveBasedOnStorageFormat(foundationStateAdditionalState, foundationOutputAdditionalFileOutput, timestamp)
  }


  private def saveBasedOnStorageFormat(foundationState: DataFrame, foundationFileOutput: String, timestamp: String): Unit ={

    // Save the foundation state based on the storage format
    if (!foundationState.rdd.isEmpty()) {
      logger.debug("Writing foundation state to file...")
      if (writeFormat == Constants.FILE_AVRO_FORMAT) {
        foundationState.write.mode("overwrite").format("avro").save(foundationFileOutput)
      }
      else if (writeFormat == Constants.FILE_PARQUET_FORMAT) {
        foundationState.write.mode("overwrite").parquet(foundationFileOutput)
      }
      else {
        foundationState.write.mode("overwrite").json(foundationFileOutput)
      }
      logger.debug(s"Foundation state available at: " + foundationFileOutput)
    }
    else
      logger.warn("No foundation state output to write at " + timestamp)


  }


  private[statecalculation] def getSynthesisedEventsLocation: List[String] = {
    // Read all files from curated folder
    val consistencyFilesFolder = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir
    val consistencyOutputFiles = fileUtils.listFilenames(consistencyFilesFolder)
    consistencyOutputFiles
  }

  private[statecalculation] def retrieveSyntheticEventsAsDS: RDD[SyntheticEvents] = {
    val synthesizedFiles = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir + File.separator + "*" + File.separator + "*"
    readFile(synthesizedFiles, readFormat).toJSON.rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[SyntheticEvents]).get)
  }

  private[statecalculation] def retrieveSyntheticEventsAsDSDelta: RDD[SyntheticEvents] = {
    val deltaPath = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir.stripSuffix("/") + File.separator + "synthesized-delta"
    if (fileUtils.exists(deltaPath)) {
      readFile(deltaPath, readFormat).toJSON.rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[SyntheticEvents]).get)
    } else {
      sparkSession.sparkContext.emptyRDD[SyntheticEvents]
    }
  }

  private[statecalculation] def retrieveSyntheticEventsAsDSDeltaAccountLevel: RDD[SyntheticEvents] = {
    val synthesizedPath = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir.stripSuffix("/")
    val deltaFiles = fileUtils.listDirectories(synthesizedPath).filter( v => (v.contains("synthesized-new") || v.contains("synthesized-changed")) && (!v.contains("synthesized-unchanged")) )
    if (deltaFiles.size > 0) {
      readFile(deltaFiles, readFormat).toJSON.rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[SyntheticEvents]).get)
    } else {
      sparkSession.sparkContext.emptyRDD[SyntheticEvents]
    }
  }

  private[statecalculation] def retrieveSyntheticEvents: RDD[SyntheticEvents] = {

    // Retrieve the synthetic events from consistency check
    val syntheticEventsFile = getSynthesisedEventsLocation

    // Create a DataFrame from the synthetic events, based on File format its stored in
    val retrievedSyntheticEvents: RDD[String] = try {
      if (readFormat == Constants.FILE_AVRO_FORMAT) {
        sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
        val syntheticEventsDF: DataFrame = sparkSession.read.format("avro").load(syntheticEventsFile: _*)
        syntheticEventsDF.toJSON.rdd
      }
      else if (readFormat == Constants.FILE_PARQUET_FORMAT) {
        val syntheticEventsDF = sparkSession.read.parquet(syntheticEventsFile: _*)
        syntheticEventsDF.toJSON.rdd
      }
      else {
        val syntheticEventsDF = sparkSession.read.json(syntheticEventsFile: _*)
        syntheticEventsDF.toJSON.rdd
      }
    }
    catch {
      case _: FileNotFoundException => {
        logger.error("Synthetic events file not found")
        sparkSession.emptyDataFrame.toJSON.rdd
      }
    }

    val syntheticEvents = retrievedSyntheticEvents.map(each => JsonUtilExperimental.fromJson[SyntheticEvents](each))
    syntheticEvents
  }

  def writeFromDataset(sparkSession: SparkSession, data: RDD[XpmModel], timestamp: String): Boolean = {
    val location = StateCalcIOUtils.makeCalculatedOutputPath(timestamp, oxygenConf)
    val frame = DataFrameConverters.forStateCalculator(sparkSession, data)
    ioUtils.write.dataFrame(frame, location, writeFormat)
  }

  def writeFromDatasetDelta(sparkSession: SparkSession, data: RDD[XpmModel], timestamp: String): Boolean = {
    val location = StateCalcIOUtils.makeCalculatedOutputPathDelta(timestamp, oxygenConf)
    val frame = DataFrameConverters.forStateCalculator(sparkSession, data)
    ioUtils.write.dataFrame(frame, location, writeFormat)
  }

  def readFile(filename: String, readFormat: String): DataFrame = {
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

  def readFile(filenames: List[String], readFormat: String): DataFrame = {
    readFormat match {
      case "avro" => {
        if (filenames.size <= 1) {
          appContext.sparkSession.read.format("avro").option("encoding", "UTF-8").load(filenames: _*)
        } else {
          val validFilesJsonDf = filenames
            .filter(file => fileUtils.listFilenames(file).filter(v => (!v.contains(".DS_Store")) && (!v.contains("_SUCCESS"))).size > 0)
            .map(v => appContext.sparkSession.read.format("avro").option("encoding", "UTF-8").load(v).toJSON)
            .reduceLeft(_.union(_))
          appContext.sparkSession.read.option("encoding", "UTF-8").json(validFilesJsonDf)
        }
      }
      case "parquet" => sparkSession.read.option("encoding", "UTF-8").option("mergeSchema", "true").parquet(filenames: _*)
      case "text" => sparkSession.read.option("encoding", "UTF-8").json(filenames: _*)
      case _ => throw new UnsupportedOperationException
    }
  }

  def checkAndRemoveEmptyDir(dir: String, format: String) = {
    val fileToCheck = dir
    if (fileUtils.exists(fileToCheck)) {
      val isStateEmpty = readFile(fileToCheck, format).isEmpty
      if (isStateEmpty) {
        fileUtils.deleteFileOrFolder(fileToCheck)
        logger.error(s"Output is empty and removed: $fileToCheck")
      } else logger.info(s"Output checked and is not empty: $fileToCheck")
    } else logger.info(s"Output checked and is not empty: $fileToCheck")
  }

  def getAndSaveUnchanged(foundationUnchangedDir: String, additionalFoundationUnchangedDir: String, calculatedUnchangedDir: String, foundationReferenceDir: String, additionalFoundationReferenceDir: String, calculatedReferenceDir: String, deletedPath: String, changedPath: String): Unit = {

    val colPin = "pin"
    val deletedDf = if (fileUtils.exists(deletedPath)) readFile(deletedPath, readFormat) else sparkSession.emptyDataFrame
    val changedDf = if (fileUtils.exists(changedPath)) readFile(changedPath, readFormat).select(colPin) else sparkSession.emptyDataFrame
    val deletedAndChangedDf = if (!deletedDf.isEmpty) {
      if (!changedDf.isEmpty) deletedDf.union(changedDf) else deletedDf
    } else {
      if (!changedDf.isEmpty) changedDf else sparkSession.emptyDataFrame
    }
    deletedAndChangedDf.persist(StorageLevel.DISK_ONLY)

    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_FOUNDATION)) {
      logger.info("Saving Foundation State Unchanged")
      if (fileUtils.exists(foundationReferenceDir.stripSuffix("/*/*")) && fileUtils.listFilenames(foundationReferenceDir.stripSuffix("/*/*")).filter(!_.contains("DS_Store")).size > 0) {
        val referenceFoundationDf = readFile(foundationReferenceDir, readFormat)
        if (!referenceFoundationDf.isEmpty) {
          val foundationCols = referenceFoundationDf.columns
          val unchangedFoundation = if (!deletedAndChangedDf.isEmpty) {
            referenceFoundationDf.join(broadcast(deletedAndChangedDf), Seq(colPin), "left_anti").select(foundationCols.head, foundationCols.tail: _*)
          } else {
            referenceFoundationDf
          }
          saveBasedOnStorageFormat(unchangedFoundation, foundationUnchangedDir, "")
          checkAndRemoveEmptyDir(foundationUnchangedDir, writeFormat)
        }
      } else {
        logger.info("Foundation State Unchanged is empty")
      }
    } else {
      logger.info("Skipping writing Foundation State Unchanged")
    }

    if (oxygenConf.oxygen.isFeatureActive(Constants.features.ADDITIONAL_STATECALC_OUTPUT_FOUNDATION)) {
      logger.info("Saving Additional Foundation State Unchanged")
      if (fileUtils.exists(additionalFoundationReferenceDir.stripSuffix("/*/*")) && fileUtils.listFilenames(additionalFoundationReferenceDir.stripSuffix("/*/*")).filter(!_.contains("DS_Store")).size > 0) {
        val referenceAdditionalFoundationDf = readFile(additionalFoundationReferenceDir, readFormat)
        if (!referenceAdditionalFoundationDf.isEmpty) {
          val additionalFoundationCols = referenceAdditionalFoundationDf.columns
          val unchangedAdditionalFoundation = if (!deletedAndChangedDf.isEmpty) {
            referenceAdditionalFoundationDf.join(broadcast(deletedAndChangedDf), Seq(colPin), "left_anti").select(additionalFoundationCols.head, additionalFoundationCols.tail: _*)
          } else {
            referenceAdditionalFoundationDf
          }
          saveBasedOnStorageFormat(unchangedAdditionalFoundation, additionalFoundationUnchangedDir, "")
          checkAndRemoveEmptyDir(additionalFoundationUnchangedDir, writeFormat)
        }
      } else {
        logger.info("Additional Foundation State Unchanged is empty")
      }
    } else {
      logger.info("Skipping writing Additional Foundation State")
    }

//    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_AS_TSV)) {
//      StateCalcIOUtils.saveCalculationAsTSV(
//        ioUtils, stateCalcConfig.paths.calculatedTsvOutput, timestamp, aggregation
//      )
//    }
//    else logger.info("Skipping TSV Output")

    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_CALCULATION)) {
      logger.info("Saving Calculated Unchanged")
      if (fileUtils.exists(calculatedReferenceDir.stripSuffix("/*/*")) && fileUtils.listFilenames(calculatedReferenceDir.stripSuffix("/*/*")).filter(!_.contains("DS_Store")).size > 0) {
        val referenceCalculatedDf = readFile(calculatedReferenceDir, readFormat)
        if (referenceCalculatedDf.isEmpty) throw new Exception("Calculated in reference generation is empty! " + calculatedReferenceDir)
        val calculatedCols = referenceCalculatedDf.columns
        val unchangedCalculated = if (!deletedAndChangedDf.isEmpty) {
          referenceCalculatedDf.join(broadcast(deletedAndChangedDf), Seq(colPin), "left_anti").select(calculatedCols.head, calculatedCols.tail: _*)
        } else {
          referenceCalculatedDf
        }
        ioUtils.write.dataFrame(unchangedCalculated, calculatedUnchangedDir, writeFormat)
        checkAndRemoveEmptyDir(calculatedUnchangedDir, writeFormat)
      } else {
        logger.error("Calculated Unchanged output is empty.")
      }
    } else {
      logger.info("Skipping Writing Calculation Output")
    }

    deletedAndChangedDf.unpersist
  }

  def getAndSaveUnchangedV2(foundationUnchangedDir: String, additionalFoundationUnchangedDir: String, calculatedUnchangedDir: String, foundationReferenceDir: String, additionalFoundationReferenceDir: String, calculatedReferenceDir: String, unchangedPath: String, doesComputeXpm: Boolean): Unit = {

    val colPin = "pin"
    val wildcardSymbol = File.separator + "*" + File.separator + "*"
    val unchangedDfRead = readFile(unchangedPath, readFormat)

    if (!unchangedDfRead.isEmpty) {
      val unchangedDf = unchangedDfRead.select(colPin).persist()

      if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_FOUNDATION)) {
        logger.info("Saving Foundation State Unchanged")
        if (fileUtils.exists(foundationReferenceDir.stripSuffix(wildcardSymbol)) && fileUtils.listFilenames(foundationReferenceDir.stripSuffix(wildcardSymbol)).filter(!_.contains("DS_Store")).size > 0) {
          val referenceFoundationDf = readFile(foundationReferenceDir, readFormat)
          if (!referenceFoundationDf.isEmpty) {
            val foundationCols = referenceFoundationDf.columns

            val unchangedFoundation = referenceFoundationDf
              .join(unchangedDf, Seq(colPin), "right")
              .select(foundationCols.head, foundationCols.tail: _*)
            saveBasedOnStorageFormat(unchangedFoundation, foundationUnchangedDir, "")
//            checkAndRemoveEmptyDir(foundationUnchangedDir, writeFormat)
          }
        } else {
          logger.info("Foundation State Unchanged is empty")
        }
      } else {
        logger.info("Skipping writing Foundation State Unchanged")
      }

      if (oxygenConf.oxygen.isFeatureActive(Constants.features.ADDITIONAL_STATECALC_OUTPUT_FOUNDATION)) {
        logger.info("Saving Additional Foundation State Unchanged")
        if (fileUtils.exists(additionalFoundationReferenceDir.stripSuffix(wildcardSymbol)) && fileUtils.listFilenames(additionalFoundationReferenceDir.stripSuffix(wildcardSymbol)).filter(!_.contains("DS_Store")).size > 0) {
          val referenceAdditionalFoundationDf = readFile(additionalFoundationReferenceDir, readFormat)
          if (!referenceAdditionalFoundationDf.isEmpty) {
            val additionalFoundationCols = referenceAdditionalFoundationDf.columns

            val unchangedAdditionalFoundation = referenceAdditionalFoundationDf
              .join(unchangedDf, Seq(colPin), "right")
              .select(additionalFoundationCols.head, additionalFoundationCols.tail: _*)
            saveBasedOnStorageFormat(unchangedAdditionalFoundation, additionalFoundationUnchangedDir, "")
//            checkAndRemoveEmptyDir(additionalFoundationUnchangedDir, writeFormat)
          }
        } else {
          logger.info("Additional Foundation State Unchanged is empty")
        }
      } else {
        logger.info("Skipping writing Additional Foundation State")
      }

      //    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_AS_TSV)) {
      //      StateCalcIOUtils.saveCalculationAsTSV(
      //        ioUtils, stateCalcConfig.paths.calculatedTsvOutput, timestamp, aggregation
      //      )
      //    }
      //    else logger.info("Skipping TSV Output")

      if (doesComputeXpm) {
        if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_CALCULATION)) {
          logger.info("Saving Calculated Unchanged")
          if (fileUtils.exists(calculatedReferenceDir.stripSuffix(wildcardSymbol)) && fileUtils.listFilenames(calculatedReferenceDir.stripSuffix(wildcardSymbol)).filter(!_.contains("DS_Store")).size > 0) {
            val referenceCalculatedDf = readFile(calculatedReferenceDir, readFormat)
            if (referenceCalculatedDf.isEmpty) throw new Exception("Calculated in reference generation is empty! " + calculatedReferenceDir)
            val calculatedCols = referenceCalculatedDf.columns

            val unchangedCalculated = referenceCalculatedDf
              .join(unchangedDf, Seq(colPin), "right")
              .select(calculatedCols.head, calculatedCols.tail: _*)
            ioUtils.write.dataFrame(unchangedCalculated, calculatedUnchangedDir, writeFormat)
            //          checkAndRemoveEmptyDir(calculatedUnchangedDir, writeFormat)
          } else {
            logger.error("Calculated Unchanged output is empty.")
          }
        } else {
          logger.info("Skipping Writing Calculation Output")
        }
      }

      unchangedDf.unpersist()
    }
  }

  private[statecalculation] def uploadToHbase(aggregation: RDD[XpmModel], timestamp: String): Unit = {
    val hbaseTableName = oxygenConf.configMap.getOrElse(StateCalcConfig.BATCH_STATE_STORE, oxygenConf.configMap.getOrElse(StateCalcConfig.HBASE_TABLE_NAME_OLD, "oxygen:states-batch"))
    val outputFormat = oxygenConf.configMap.getOrElse(StateCalcConfig.OUTPUT_FORMAT, "file")
    if (outputFormat.equalsIgnoreCase("hbase") || writeFormat.equalsIgnoreCase("hbase")) {
      // hbase
      logger.debug(s"Attempting to write XPM to HBase table: " + hbaseTableName)
      aggregation.foreachPartition(iter => {
        val oxygenDataAccess = new HbaseDataAccess(new HbaseConnectionContext())
        iter.foreach(each => {
          StateCalculator.saveStateEvents(each.pin, ScalaJacksonUtil.toJsonWithoutOption(each.data), hbaseTableName, oxygenDataAccess)
        })
      })
      logger.debug("Succeeded in writing XPM to hbase")
    }
  }

  private[statecalculation] def saveState(foundation: RDD[FoundationModel], aggregation: RDD[XpmModel],
                                   doesComputeXpm: Boolean = true, doesArchive: Boolean = true): Unit = {
    val outputFormat = oxygenConf.configMap.getOrElse(StateCalcConfig.OUTPUT_FORMAT, "file")

    val timestamp = String.valueOf(Instant.now().getEpochSecond)
    if (doesArchive) archiveAndCleanStateCalcDir(timestamp)
    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_FOUNDATION)) {
      logger.info("Saving Foundation State")
      saveFoundationState(foundation, timestamp)
    } else {
      logger.info("Skipping writing Foundation State")
    }

    if (oxygenConf.oxygen.isFeatureActive(Constants.features.ADDITIONAL_STATECALC_OUTPUT_FOUNDATION)) {
      logger.info("Saving Additional Foundation State")
      saveAdditionalFoundationState(foundation, timestamp)
    } else {
      logger.info("Skipping writing Additional Foundation State")
    }

    if (doesComputeXpm) {
      if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_AS_TSV)) {
        StateCalcIOUtils.saveCalculationAsTSV(
          ioUtils, stateCalcConfig.paths.calculatedTsvOutput, timestamp, aggregation
        )
      }
      else logger.info("Skipping TSV Output")

      if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_CALCULATION))
        writeFromDataset(sparkSession, aggregation, timestamp)
      else
        logger.info("Skipping Writing Calculation Output")

      // todo: Get an opinion on this during code review - to my knowledge this feature isn't used (especially as we have
      // the direct bulk loader for Hydrator and support plain text outputs).
      // todo: is this a valid use case still - it is only called by the SparkStateCalculator (which I believe is Batch only)?
      // Also we are doing this check in two places (here and in the uploadToHbase method) - refactor?
      if (outputFormat.equalsIgnoreCase("hbase") || writeFormat.equalsIgnoreCase("hbase")) {
        logger.warn(
          """
            | Writing XPMs directly to Hbase is not typically advised and can cause performance issues with large
            | loads.
            |
            | Setting the StatCalc output format to 'file' will prevent writing directly to hbase.
            |
            | The recommended approach is to use the Hydrator utility to bulk load the file based output.
            |""".stripMargin
        )
        uploadToHbase(aggregation, timestamp)
      }
      else
        logger.info("Skipping uploading to HBase directly from State Calculation (used only during development)")
    }

  }

  private[statecalculation] def saveStateDelta(foundation: RDD[FoundationModel], aggregation: RDD[XpmModel],
                                        doesComputeXpm: Boolean = true, doesArchive: Boolean = true): Unit = {
    val outputFormat = oxygenConf.configMap.getOrElse(StateCalcConfig.OUTPUT_FORMAT, "file")

    val timestamp = String.valueOf(Instant.now().getEpochSecond)
    if (doesArchive) archiveAndCleanStateCalcDir(timestamp)

    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_FOUNDATION)) {
      logger.info("Saving Foundation State Delta")
      saveFoundationStateDelta(foundation, timestamp)
    } else {
      logger.info("Skipping writing Foundation State Delta")
    }

    if (oxygenConf.oxygen.isFeatureActive(Constants.features.ADDITIONAL_STATECALC_OUTPUT_FOUNDATION)) {
      logger.info("Saving Additional Foundation State Delta")
      saveAdditionalFoundationStateDelta(foundation, timestamp)
    } else {
      logger.info("Skipping writing Additional Foundation State Delta")
    }

    if (doesComputeXpm) {
      //    if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_AS_TSV)) {
      //      StateCalcIOUtils.saveCalculationAsTSV(
      //        ioUtils, stateCalcConfig.paths.calculatedTsvOutput, timestamp, aggregation
      //      )
      //    }
      //    else logger.info("Skipping TSV Output")

      if (oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_CALCULATION)) {
        logger.info("Saving Calculation Delta Output")
        writeFromDatasetDelta(sparkSession, aggregation, timestamp)
      }
      else {
        logger.info("Skipping Writing Calculation Delta Output")
      }

      //    // todo: Get an opinion on this during code review - to my knowledge this feature isn't used (especially as we have
      //    // the direct bulk loader for Hydrator and support plain text outputs).
      //    // todo: is this a valid use case still - it is only called by the SparkStateCalculator (which I believe is Batch only)?
      //    // Also we are doing this check in two places (here and in the uploadToHbase method) - refactor?
      //    if (outputFormat.equalsIgnoreCase("hbase") || writeFormat.equalsIgnoreCase("hbase")) {
      //      logger.warn(
      //        """
      //          | Writing XPMs directly to Hbase is not typically advised and can cause performance issues with large
      //          | loads.
      //          |
      //          | Setting the StatCalc output format to 'file' will prevent writing directly to hbase.
      //          |
      //          | The recommended approach is to use the Hydrator utility to bulk load the file based output.
      //          |""".stripMargin
      //      )
      //      uploadToHbase(aggregation, timestamp)
      //    }
      //    else
      //      logger.info("Skipping uploading to HBase directly from State Calculation (used only during development)")
    }

  }

}
