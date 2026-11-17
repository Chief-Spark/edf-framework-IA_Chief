package com.experian.edf.oxygen.pinning.engines

import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.vle.VleMetadataManager
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import org.apache.spark.TaskContext
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel
import org.slf4j.Logger
import org.slf4j.LoggerFactory._

import java.io.File
import scala.util.{Failure, Success, Try}

class SparkHeterogeneousCollector(appContext: SparkOxygenAppContext) extends HeterogeneousCollector(appContext) {

  @transient override lazy val logger: Logger = getLogger(this.getClass)

  private val spark = appContext.sparkSession
  private val hcNumPartitions = oxygenConf.configMap.get("oxygen.stage.heterogeneousCollection.hcNumPartitions").getOrElse("0").toInt
  private val defaultOutputStructure = oxygenConf.configMap.get("oxygen.features.enable.defaultOutputStructure").getOrElse("false").toBoolean

  override def createOrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.HETEROGENEOUSCOLLECTION,
    source = MetricsConstants.Sources.HETEROGENEOUSCOLLECTOR,
    orchestrationSource = None, orchestrationTarget = None,
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId))

  private val oxygenIO = com.experian.edf.oxygen.io.utils.OxygenIOUtils(appContext.sparkSession)
  private val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT
  private val readFormat = oxygenConf.oxygen.HC_READ_WRITE_FORMAT.input.getOrElse(storageFormat)

  private val vleFile = oxygenConf.oxygen.VLE_FILE
  private val vleManager =  if (vleFile.isDefined) VleMetadataManager.init(vleFile.get) else None
  val columnFilter = oxygenConf.oxygen.HC_COLUMN_FILTER

  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")

  private val metricsMetadata = Map(MetricsConstants.Keys.GENERATION -> oxygenConf.oxygen.generation.candidate.getOrElse("default"),
  "partitionId" -> TaskContext.getPartitionId())

  override def run(): Unit = {
    startService()
    val pinnedDirectory = getPinnedFile
    val pinnedEIDDirectory = getPinnedEIDFile
    val pinnedMapFileDirectory = getPinnedPinMapFile
    val stockCuratedDirectory = getCuratedFiles

    logger.error(s"HC repartition: ${hcNumPartitions.toString}")
    logger.error(s"Pinned EID Directory : $pinnedEIDDirectory")
    logger.error(s"defaultOutputStructure : $defaultOutputStructure")
    val pinEIDData = readData(List(pinnedEIDDirectory), oxygenConf.oxygen.HC_READ_WRITE_FORMAT.input.get)

    if (emitStockMetricsEnabled) {
      if (pinEIDData != null) {
        emitStockMetric("pinEIDDataCountPartition", "pinEIDDataCountPartition", pinEIDData.count(), metricsMetadata)
      }
      else {
        emitStockMetric("pinEIDDataCountPartition", "pinEIDDataCountPartition", 0, metricsMetadata)
      }
    }

    val pinMapDataData = readData(List(pinnedMapFileDirectory), oxygenConf.oxygen.HC_READ_WRITE_FORMAT.input.get)

    val curatedData = readData(List(stockCuratedDirectory), readFormat)
    if (pinEIDData.rdd.isEmpty()) {
      logger.error("Skipping HC generation since Pin-EID data is empty.")
      endService()
      return
    }
    if (curatedData.rdd.isEmpty()) {
      logger.error("Skipping HC generation since curated event data is empty.")
      endService()
      return
    }

    val curatedEvents = if(!columnFilter.isEmpty){
      curatedData.drop(columnFilter:_*)
    } else curatedData

    //Optionally save pins with large number of events (higher than configured threshold) before doing stamping and HC
    if (oxygenConf.oxygen.HC_CELEBRITY_PIN_THRESHOLD.isDefined) {
      val celebrityPinData = saveCelebrityPinData(pinEIDData, oxygenConf.oxygen.HC_CELEBRITY_PIN_THRESHOLD)
      celebrityPinData match {
        case Success(dataToSave) =>
          Try(fileUtils.moveFile(pinnedDirectory + "-celebrityPin", pinnedDirectory + "-celebrityPin-" + System.currentTimeMillis()))
          oxygenIO.write.dataFrame(dataToSave, pinnedDirectory + "-celebrityPin", oxygenConf.oxygen.HC_READ_WRITE_FORMAT.output.get)
        case Failure(f) =>
          logger.error(s"Pins matching threshold could not be computed. Reason ${f.getLocalizedMessage} Skipping save for celebrityPin data.")
      }
    }

    if ( vleManager.isDefined && vleManager.get.vleEnable && oxygenConf.oxygen.VLE_THRESHOLD_TYPE.isDefined) {
      val thresholdValue = oxygenConf.oxygen.VLE_THRESHOLD_TYPE.get
      val potentialVLE = saveCelebrityPins(pinEIDData, vleManager.get.thresholdValue(thresholdValue))
      val pinMapIndentityAtts = pinMapDataData.select("pin", "taggedIdentity.identity.attributes.*")
      val potentialVLEInner = potentialVLE.get.join(pinMapIndentityAtts, Seq("pin"), "inner")
      val dataToSave = potentialVLEInner.select("pin",  vleManager.get.vleKeys: _*)
      Try(fileUtils.moveFile(pinnedDirectory + "-potentialVLE", pinnedDirectory + "-potentialVLE-" + System.currentTimeMillis()))
      oxygenIO.write.dataFrame(dataToSave, pinnedDirectory + "-potentialVLE", oxygenConf.oxygen.HC_READ_WRITE_FORMAT.output.get)

    }

    val partStampedEvents: DataFrame = stampCuratedEvents(pinEIDData, curatedEvents)
    val hc = if (hcNumPartitions == 0) createHC(partStampedEvents) else createHC(partStampedEvents).repartition(hcNumPartitions)

    if (emitStockMetricsEnabled) {
      if (hc != null) {
        emitStockMetric("pinCountPartition", "pinCountPartition", hc.count(), metricsMetadata)
      }
      else {
        emitStockMetric("pinCountPartition", "pinCountPartition", 0, metricsMetadata)
      }
    }

    Try(fileUtils.moveFile(pinnedDirectory + "-hc", pinnedDirectory + "-hc-" + System.currentTimeMillis()))
    oxygenIO.write.dataFrame(hc, pinnedDirectory + "-hc", oxygenConf.oxygen.HC_READ_WRITE_FORMAT.output.get)

    if (!defaultOutputStructure) removeDefaultOutputStructure

    endService()
  }

  def removeDefaultOutputStructure(): Unit = {
    val pinnedDir = oxygenConf.oxygen.PINNED_DIRECTORY
    val reusePin = oxygenConf.oxygen.REUSE_PIN
    val referencePinnedDir = if (reusePin) oxygenConf.oxygen.PINNED_REFERENCE_DIR.getOrElse("") else ""
    val userHomeDir = oxygenConf.configMap.getOrElse("oxygen.application.paths.userHome", "")

    if (userHomeDir != "" &
      ((!pinnedDir.contains(userHomeDir)) & (!referencePinnedDir.contains(userHomeDir))) &
      fileUtils.exists(userHomeDir)) {
      fileUtils.deleteFileOrFolder(userHomeDir)
      logger.info("Default output structure removed.")
    }
  }

  def emitStockMetric(name: String, key: String, value: Long, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.HETEROGENEOUSCOLLECTION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: Double, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.HETEROGENEOUSCOLLECTION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.HETEROGENEOUSCOLLECTION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  private def readData(location: List[String], readFormat: String): DataFrame = {
    readFormat match {
      case Constants.FILE_AVRO_FORMAT => Try(spark.read.format("avro").load(location: _*)) match {
        case Success(value) => value
        case Failure(f) =>
          logger.error("Error reading avro input data " + f)
          logger.error("Returning empty data frame.")
          spark.emptyDataFrame
      }
      case Constants.FILE_PARQUET_FORMAT => Try(spark.read.parquet(location: _*)) match {
        case Success(value) => value
        case Failure(f) =>
          logger.error("Error reading parquet input data " + f)
          logger.error("Returning empty data frame.")
          spark.emptyDataFrame
      }
      case _ => Try(spark.read.json(location: _*)) match {
        case Success(value) => value
        case Failure(f) =>
          logger.error("Error reading json input data " + f)
          logger.error("Returning empty data frame.")
          spark.emptyDataFrame
      }
    }
  }

  private def getCuratedFiles: String = {
    val stockCuratedFile = oxygenConf.oxygen.stage.curation.get.paths.curatedDir + File.separator + "*/**"
    stockCuratedFile
  }

  private def getPinnedFile: String = {
    val pinnedFile = fileUtils.listDirectories(oxygenConf.oxygen.PINNED_DIRECTORY, recursive = true)
      .filter(eachFile => eachFile.endsWith("-pin-EID"))
    if (pinnedFile.lengthCompare(1) >= 0) {
      logger.error("More than one Pinned files. Picking Last file")
      val pf = pinnedFile.last
      return pf.substring(0, pf.length - 4)
    }
    logger.error("No matching Pinned files.")
    ""
  }

  private def getPinnedEIDFile: String = {
    val pinnedFile = fileUtils.listDirectories(oxygenConf.oxygen.PINNED_DIRECTORY, recursive = true)
      .filter(eachFile => eachFile.endsWith("-pin-EID"))
    if (pinnedFile.isEmpty) {
      logger.error("No matching Pinned files.")
      return ""
    }
    if (pinnedFile.length > 1) {
      logger.error("More than one Pinned files. Picking Last file")
      pinnedFile.last
    } else
      pinnedFile.last
  }

  private def getPinnedPinMapFile: String = {
    val pinnedFile = fileUtils.listDirectories(oxygenConf.oxygen.PINNED_DIRECTORY, recursive = true)
      .filter(eachFile => eachFile.endsWith("-identity-pin-map"))
    if (pinnedFile.isEmpty) {
      logger.error("No matching Pinned files.")
      return ""
    }
    if (pinnedFile.length > 1) {
      logger.error("More than one Pinned files. Picking Last file")
      pinnedFile.last
    } else
      pinnedFile.last
  }

  private def stampCuratedEvents(pinEIDData: DataFrame, curatedEvents: DataFrame): DataFrame = {
    val pinEIDDataRepartitioned = if (curatedEvents.rdd.getNumPartitions > 0) pinEIDData.repartition(curatedEvents.rdd.getNumPartitions, col("eventId")) else pinEIDData
    val taggedFieldsNames = curatedEvents.schema.fieldNames
    val curatedJoin = curatedEvents.withColumn("taggedCuratedEvent", struct(taggedFieldsNames.map(c => col(c)): _*))
      .select("eventId", "taggedCuratedEvent")

    val stampedEvents = curatedJoin.join(pinEIDDataRepartitioned, Seq("eventId"))
    val partStampedEvents = if (curatedEvents.rdd.getNumPartitions > 0) stampedEvents.repartition(curatedEvents.rdd.getNumPartitions, col("pin")) else stampedEvents
    partStampedEvents
  }

  private[oxygen] def createHC(pinnedEvents: DataFrame): DataFrame = {
    pinnedEvents
      .groupBy("pin")
      .agg(collect_list("taggedCuratedEvent") as "events")
  }

  private def saveCelebrityPinData(pinEIDData: DataFrame, thresholdOpt: Option[Int]): Try[DataFrame] = {
    if (thresholdOpt.isEmpty || (thresholdOpt.isDefined && thresholdOpt.get <= 0)) {
      return Failure(new UnsupportedOperationException("Incorrect configuration: HC_CELEBRITY_PIN_THRESHOLD is invalid!"))
    }
    val threshold = thresholdOpt.get

    logger.info(s"Computing celebrity pins: Threshold $threshold")
    val celebrityPins = findCelebrityPins(pinEIDData, threshold = threshold).persist(StorageLevel.MEMORY_AND_DISK_SER)
    Success(celebrityPins)
  }

  private[oxygen] def findCelebrityPins(pinEIDData: DataFrame, threshold: Int): DataFrame = {
    val celebrity = pinEIDData.groupBy("pin").agg(count("*").as("count"))
      .filter(s"`count` >= $threshold").select("pin")
    celebrity
  }

  private[oxygen] def saveCelebrityPins(pinEIDData: DataFrame, amountVlE: Long): Try[DataFrame] = {
    val celebrity = pinEIDData.groupBy("pin").agg(count("*").as("count"))
      .filter(s"`count` >= $amountVlE").select("pin", "count")
    Success(celebrity)
  }


}


