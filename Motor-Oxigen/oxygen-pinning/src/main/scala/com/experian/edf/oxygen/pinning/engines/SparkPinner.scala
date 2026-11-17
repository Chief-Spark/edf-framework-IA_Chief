package com.experian.edf.oxygen.pinning.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context._
import com.experian.edf.oxygen.dataaccess.hbase.{HbaseConnectionContext, HbaseDataAccess}
import com.experian.edf.oxygen.domain.internal.{ContextPinMapEvent, Identity, StampedIdentity}
import com.experian.edf.oxygen.domain.pinning.PinIndex
import com.experian.edf.oxygen.domain.pinning.SparkPinnerCommon.readPinIndexFileAsString
import com.experian.edf.oxygen.domain.processors.MigrationProcessorContext
import com.experian.edf.oxygen.domain.vle.VleRecord
import com.experian.edf.oxygen.domain.{PinnedEventWithMap, TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedIdentity}
import com.experian.edf.oxygen.global.utils.{OxygenCollectionsUtils, ResourceUtils}
import com.experian.edf.oxygen.logging.ProcessUpdateEvent
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.pinning.domain.{HeterogeneousCollection, PinnedTaggedIdentity}
import com.experian.edf.oxygen.pinning.lookup.RelationshipMigrationProcessor
import com.experian.edf.oxygen.pinning.util._
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
//import com.experian.edf.oxygen.reasonability.{ReasonabilityObject, ReasonabilityProcess}
import com.experian.edf.oxygen.utils.{JsonUtilExperimental, JsonUtils, ScalaJacksonUtil}
import org.apache.avro.Schema
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.apache.spark.TaskContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.{col, collect_list, collect_set, expr}
import org.apache.spark.sql.{DataFrame, Encoder, SparkSession}
import org.apache.spark.storage.StorageLevel
import org.graphframes._

import java.io.{ByteArrayOutputStream, File, FileNotFoundException}
import java.time.Instant
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

case class KaAttributes(attributes: Map[String, Any])
case class KaIdentities(identities: List[KaAttributes])
case class KtIdentities(identities: List[Map[String, Any]])
case class IdMigrationConf(identityMigrationFilter: Map[String, Any],
                           identityMigrationFields: Array[Map[String, String]])

object SparkPinner {

  /**
   * Valid formats that can be used with saveStampedIdentity and saveNearNeighbors
   */
  val validStateOutputFormats = Seq(
    Constants.FILE_TSV_FORMAT,
    Constants.FILE_AVRO_FORMAT,
    Constants.FILE_PARQUET_FORMAT,
    Constants.FILE_TEXT_FORMAT,
    Constants.JSON_FORMAT
  )

  /**
   * Get the output format to be used when saving NearNeighbors
   */
  def getNearNeighborsOutputFormat(oxygenConf: OxygenConfig): String = oxygenConf.oxygen.PINNING_SAVE_NEAR_NEIGHBOR_STATE_FORMAT

  /**
   * Get the output format to be used when saving StampedIdnetities
   */
  def getStampedIdentityOutputFormat(oxygenConf: OxygenConfig): String = oxygenConf.oxygen.PINNING_SAVE_STAMPED_IDENTITIES_STATE_FORMAT

  /**
   * Writes the Stamped Identity data to disk to allow bulk loading to a KeyValue store.
   *
   * @param oxygenIO - The OxygenIOUtils to use for writing
   * @param spark - A spark session
   * @param stampedIdentities - The stamped identity data to save
   * @param filename - location to write to
   * @param format - format to write as - see SparkPinner.validStateOutputFormats
   */
  private[oxygen] def saveStampedIdentity(
    oxygenIO: OxygenIOUtils,
    spark: SparkSession,
    stampedIdentities: RDD[StampedIdentity],
    filename: String,
    format: String
  ): Unit = {
    if (!SparkPinner.validStateOutputFormats.contains(format)) {
      throw new Exception(s"Invalid Stamped Identities output format. Must be one of '${SparkPinner.validStateOutputFormats.mkString(",")}'")
    }

    format match {
      case Constants.FILE_TSV_FORMAT => {
        val data = stampedIdentities.map(x => s"${x.identityId}\t${ScalaJacksonUtil.toJson(x).get}")
        oxygenIO.write.asTextFile(data, filename)
      }
      case _ => {
        val df = DataFrameConverters.forStampedIdentity(spark, stampedIdentities)
        oxygenIO.write.dataFrame(df, filename, format, None)
      }
    }
  }

  /**
   * Writes the Near Neighbor data to disk to allow bulk loading to a KeyValue store.
   *
   * @param oxygenIO - The OxygenIOUtils to use for writing
   * @param spark - A spark session
   * @param nearNeighbors - The near neighbor data to save
   * @param filename - location to write to
   * @param format - format to write as - see SparkPinner.validStateOutputFormats
   */
  private[oxygen] def saveNearNeighbors(
    oxygenIO: OxygenIOUtils,
    spark: SparkSession,
    nearNeighbors: RDD[(String, Array[TaggedIdentity])],
    filename: String,
    format: String
  ): Unit = {
    if (!SparkPinner.validStateOutputFormats.contains(format)) {
      throw new Exception(s"Invalid Near Neighbors output format. Must be one of '${SparkPinner.validStateOutputFormats.mkString(",")}'")
    }

    format match {
      case Constants.FILE_TSV_FORMAT => {
        val data = nearNeighbors.map(nn => s"${nn._1}\t${ScalaJacksonUtil.toJson(nn._2).get}")
        oxygenIO.write.asTextFile(data, filename)
      }
      case _ => {
        val df = DataFrameConverters.forTaggedIdentity(spark, nearNeighbors)
        oxygenIO.write.dataFrame(df, filename, format, None)
      }
    }
  }

  /**
   * Convert near neighbor RDD of type RDD[(String, ListBuffer[(Long, TaggedIdnetity)])]
   * to a format to be used with the SparkPinner.saveNearNeighbor method.
   *
   * @param nearNeighbors The near neighbor data to convert
   * @return RDD compatible with SparkPinner.saveNearNeighbor
   */
  private[oxygen] def convertNearNeighborsForSaving(
    nearNeighbors: RDD[(String, ListBuffer[(Long, TaggedIdentity)])]
  ): RDD[(String, Array[TaggedIdentity])] = {
    nearNeighbors.map(nn => {
      (nn._1, nn._2.map(_._2).toArray)
    })
  }

  /**
   * Convert near neighbor RDD of type RDD[(String, ListBuffer[TaggedIdentityWithEventId])]
   * to a format to be used with the SparkPinner.saveNearNeighbor method.
   *
   * @param nearNeighbors The near neighbor data to convert
   * @return RDD compatible with SparkPinner.saveNearNeighbor
   */
  private[oxygen] def convertSingleBlockingKeyNearNeighborsForSaving(
    nearNeighbors: RDD[(String, ListBuffer[TaggedIdentityWithEventId])]
  ): RDD[(String, Array[TaggedIdentity])] = {
    nearNeighbors.map(nn => {
      (nn._1, nn._2.map(_.taggedIdentity).toArray)
    })
  }

}


/**
 * Spark Pinner
 *
 * Performs pinning on curated events
 *
 */
class SparkPinner(appContext: SparkOxygenAppContext) extends Pinner(appContext)
{

  import appContext.sparkSession.implicits._

  val sparkSession = appContext.sparkSession // Spark session
  val stageName = MetricsConstants.Actions.PINNING

  val timestamp = System.currentTimeMillis().toString
  val edgenodeHostname = java.net.InetAddress.getLocalHost.getHostName
  val pid = ResourceUtils.getProcessID

  val pinningIOUtils = new PinningIOUtils(fileUtils, appContext)
  val oxygenIO = OxygenIOUtils(appContext.sparkSession)

  val domainDictionaryFile = try {
    Some(appContext.config.configMap("oxygen.stage.pinning.paths.domainDictionaryFile")).get
  } catch {
    case e: NoSuchElementException => {
      logger.warn("oxygen.stage.pinning.paths.domainDictionaryFile is not set.")
      ""
    }
  }

  val identityMigrationFile = try {
    Some(appContext.config.configMap("oxygen.stage.pinning.paths.identityMigrationFile")).get
  } catch {
    case e: NoSuchElementException => {
      logger.warn("oxygen.stage.pinning.paths.identityMigrationFile is not set.")
      ""
    }
  }

  /**
   * Call data from Config Files.
   */
  val enableProfiling = oxygenConf.oxygen.ENABLE_PROFILING
  val enableDoesMatchProfiling = oxygenConf.oxygen.ENABLE_DOESMATCH_PROFILING
  val pinnedDir = oxygenConf.oxygen.PINNED_DIRECTORY
//  val requiresConnectedComponent = !oxygenConf.oxygen.SINGLE_NEAR_NEIGHBOR_KEY
  val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT
  val readFormat = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.input.getOrElse(storageFormat)
  val writeFormat = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.output.getOrElse(storageFormat)
  val identityMatcherClassName = oxygenConf.oxygen.IDENTITY_MATCHER
  val clusterSplitterClassName = oxygenConf.oxygen.CLUSTER_SPLITTER
  val identifyingAttributesFile = oxygenConf.oxygen.IDENTIFYING_ATTRIBUTES_FILE
  val reusePin = oxygenConf.oxygen.REUSE_PIN
  val hfBatchStaticEnabled = appContext.config.oxygen.isFeatureActive(Constants.features.ENABLE_HF_BATCH_STATIC_PIN_INTERGRATION)
  val shouldSaveHeterogeneousCollection = oxygenConf.oxygen.SAVE_HETEROGENEOUS_COLLECTION
  val repartitionFactor = oxygenConf.oxygen.REPARTITION_FACTOR
  val pinnedFilename = newPinFilename(timestamp)
  val relationshipMigrationProcessorClassName = oxygenConf.oxygen.stage.consistency.get.relationshipMigrationProcessorClassName

  private val spark = appContext.sparkSession
  private val vleFile = oxygenConf.oxygen.VLE_FILE

  private val vleManager = if(vleFile.isDefined)  VleMetadataManager.init(vleFile.get) else None
  private val vleManagerBC = spark.sparkContext.broadcast(vleManager)

  val saveNearNeighborState = oxygenConf.oxygen.PINNING_SAVE_NEAR_NEIGHBOR_STATE
  val saveStampedIdentityState = oxygenConf.oxygen.PINNING_SAVE_STAMPED_IDENTITIES_STATE
  val nearNeighborBlockSize = oxygenConf.oxygen.PINNING_NEAR_NEIGHBOR_BLOCKSIZE
  val stampingByHC = !oxygenConf.oxygen.SAVE_HETEROGENEOUS_COLLECTION
  val enableLargeBlockHandling = oxygenConf.oxygen.ENABLE_LARGE_BLOCK_HANDLING
  val groupSSR = oxygenConf.oxygen.GROUP_SSR
  val pinningVleRange = oxygenConf.oxygen.PINNING_VLE_RANGE
  val connectedComponentsTimeout = oxygenConf.oxygen.CONNECTED_COMPONENTS_TIMEOUT
  val pinIndexFile = oxygenConf.configMap.getOrElse("oxygen.stage.pinning.paths.pinIndexFile", "")

  val KafkaMsg = new KafkaMsg(oxygenConf)
  //private val reasonability = appContext.config.oxygen.PINNING_REASONABILITY
  //private val metrics = new ReasonabilityProcess()
  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")
  private val metricsMetadata = Map(
    MetricsConstants.Keys.GENERATION -> oxygenConf.oxygen.generation.candidate.getOrElse("default"),
    "partitionId" -> TaskContext.getPartitionId())
  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.PINNING,
    source = MetricsConstants.Sources.PINNER,
    orchestrationSource = oxygenConf.oxygen.PINNED_ORCHESTRATION.source,
    orchestrationTarget = oxygenConf.oxygen.PINNED_ORCHESTRATION.target,
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId)
  )

  @throws(classOf[OxygenException])
  override def run: Unit = {
    startService()
    import spark.implicits._

    implicit val kaIdentitiesKryoEncoder: Encoder[KaIdentities] = org.apache.spark.sql.Encoders.kryo[KaIdentities]
    implicit val kaAttributesKryoEncoder: Encoder[KaAttributes] = org.apache.spark.sql.Encoders.kryo[KaAttributes]
    implicit val ktIdentitiesKryoEncoder: Encoder[KtIdentities] = org.apache.spark.sql.Encoders.kryo[KtIdentities]
    implicit val TaggedIdentityWithEventIdKryoEncoder: Encoder[TaggedIdentityWithEventId] = org.apache.spark.sql.Encoders.kryo[TaggedIdentityWithEventId]

    println("enableProfiling: " + enableProfiling)
    println("Dev Mode: " + appContext.config.oxygen.DEV_MODE)
    println("Storage Format: " + storageFormat)
    println("Read Format: " + readFormat)
    println("Write Format: " + writeFormat)
    println("identityMatcherClassName: " + identityMatcherClassName)
    println("clusterSplitterClassName: " + clusterSplitterClassName)
    println("identifyingAttributesFile: " + identifyingAttributesFile)
    println("reusePin: " + reusePin)
    println("shouldSaveHeterogeneousCollection: " + shouldSaveHeterogeneousCollection)
    println("pinnedDir: " + pinnedDir)
    println("Repartition Factor: " + repartitionFactor)
    println("Curated Directory:" + oxygenConf.oxygen.stage.curation.get.paths.curatedDir)
    println("Celebrity File:" + oxygenConf.oxygen.CELEBRITY_FILE)
    println("Celebrity Key:" + oxygenConf.oxygen.CELEBRITY_KEY)
    println("VLE file: " + vleFile)
    println("save NearNeighbor State :" + oxygenConf.oxygen.PINNING_SAVE_NEAR_NEIGHBOR_STATE)
    println("save StampedIdentity State:" + oxygenConf.oxygen.PINNING_SAVE_STAMPED_IDENTITIES_STATE)
    println("near Neighbor BlockSize:" + oxygenConf.oxygen.PINNING_NEAR_NEIGHBOR_BLOCKSIZE)
    println(s"enableLargeBlockHandling: $enableLargeBlockHandling")
//    println("requiresConnectedComponent: " + requiresConnectedComponent)
    println("groupSSR: " + groupSSR)
    println("pinningVleRange:  0 -" + pinningVleRange)
    println("connectedComponentsTimeout: " + connectedComponentsTimeout.toString + " ms")
    println("identityMigrationFile: " + identityMigrationFile)
    println("relationshipMigrationProcessorClassName: " + relationshipMigrationProcessorClassName)
    println("pinIndexFile: " + pinIndexFile)

    /**
     * Get data to process.
     */
    val newDatasets = orchestrator.retrieveAllDatasetsToProcess()
    if (appContext.config.oxygen.DEV_MODE || (newDatasets.isDefined && newDatasets.get.nonEmpty)) {

      /**
       * Log start of pinning.
       */
      logPinningProcessStarted()

      /**
       * Read identifying attributes.
       */
      val identifyingAttributesBC = appContext.sc.broadcast(readIdentifyingAttributes(identifyingAttributesFile.head))

      val identityPinMapRealTimeBC = appContext.sc.broadcast(appContext.config.configMap(Constants.storesConfigKey.PINNING_IDENTITY_PIN_MAP_FQ))

      /**
       * Create data access for identityPinMap.
       */
      val identityPinMapDAO = if (hfBatchStaticEnabled) {
        val oxygenDataAccess = new HbaseDataAccess(new HbaseConnectionContext())

        val identityPinMapDAOObject = new IdentityPinMapDAO(
          oxygenDataAccess,
          identityPinMapRealTimeBC.value
        )
        identityPinMapDAOObject
      }
      else new IdentityPinMapDAO(null, null)

      /**
       * Read Celebrity File
       */
      //val celebrityIds = if (celebrityFile != null && celebrityFile != "")
      //  readCelebrities(celebrityFile)
      //else List.empty

      /**
       * Read VLE file
       */
      val celebrityIds = if (vleFile.isDefined) vleManagerBC.value.get.vleValues else Map.empty[String,VleRecord]

      /**
       * read curated records
       */
      val curatedEvents = readCuratedEvents(appContext, List.empty[String], readFormat)
      // Skip pinning and avoid unnecessary archival if there are no curated events to process.
      if (curatedEvents.isEmpty()) {
        logger.error("Skipping pinning since there are no curated events to process.")
        endService()
        return
      }

      /**
       * Get non-VLE events.
       */
      val nonCelebrityEvents = if (celebrityIds.nonEmpty) filterNoneVleEvents(curatedEvents, celebrityIds) else curatedEvents

      /**
       * Read KT records
       */
      val ktFiles = retrieveKeepTogetherFiles
      logger.error("Keep Together files path" + ktFiles)
      val ktRecords = readKeepTogetherRecords(ktFiles)

      /**
       * Read KA records
       */
      val kaFilesFolder = oxygenConf.oxygen.KEEP_APART_DIRECTORY
      logger.error("Keep Apart files path: " + kaFilesFolder)
      val kaTaggedIdentitiesBC = if (fileUtils.exists(kaFilesFolder) && fileUtils.listFilenames(kaFilesFolder).size > 0)
          readKeepApartFiles(kaFilesFolder, identifyingAttributesBC)
        else spark.sparkContext.broadcast(List.empty[List[TaggedIdentity]])

      /**
       * Read ID Migration conf
       */
      val idMigrationConf = if (!identityMigrationFile.isEmpty && identityMigrationFile.size > 0) {
        val idMigrationConfStr = readIdentityMigrationConfAsString(identityMigrationFile)
        convertStringToIdMigrationConf(idMigrationConfStr)
      } else emptyIdMigrationConf

      /**
       * Initialize lookup service. Broadcasted oxygen lookup service has to be passed in the function calls to avail the service else it is set to null.
       */
      val lookupService = OxygenLookupService.getServiceB.value
      val migrationProcessorContext: MigrationProcessorContext = MigrationProcessorContext(lookupService)
      /**
       * If not migrationProcessor specified, use the default one.
       */
      val relationshipMigrationProcessor = if (relationshipMigrationProcessorClassName == null || relationshipMigrationProcessorClassName == "")
        null
      else
        Class.forName(relationshipMigrationProcessorClassName.trim).getConstructor(migrationProcessorContext.getClass).newInstance(migrationProcessorContext).asInstanceOf[RelationshipMigrationProcessor]

      /**
       * Get hc, near-neighbor output filenames and formats
       */
      val hcFileName = hcFilename(timestamp)
      val nearNeighborFilename = newNearNeighborFilename(pinnedFilename)
      val nearNeighborFormat =  SparkPinner.getNearNeighborsOutputFormat(oxygenConf)
      val archivedDir = archivePinDir(timestamp)
      /**
       * Read IdentityPinMap files if needed.
       * Check the current active generation.
       */
      val lastGenIdentityPinMap = if (reusePin && oxygenConf.oxygen.PINNED_REFERENCE_DIR.isDefined) {
        val hfIdentityPinMap = if (hfBatchStaticEnabled) readFromHbaseForIdentityPinMap(identityPinMapDAO)
        else null

        val identityPinMapFilename = getLastGenIdentityPinMapFilename(oxygenConf.oxygen.PINNED_REFERENCE_DIR.get).orNull
        logger.debug(s"Reference Pinned File : $identityPinMapFilename")
        val batchIdentityPinMap = if (identityPinMapFilename != null) {
          logger.error("Reading last generation file: " + identityPinMapFilename)
          readIdentityPinMapFile(identityPinMapFilename)
        }
        else {
          logger.warn("Did not find previous generation pins.  Will generate new Pins")
          null
        }

        if(hfIdentityPinMap != null) batchIdentityPinMap.union(hfIdentityPinMap)
        else batchIdentityPinMap

      } else null

      if (lastGenIdentityPinMap != null) {
        lastGenIdentityPinMap.persist(StorageLevel.MEMORY_AND_DISK_SER)
      }

      val (stampedEvents, stampedIdentity, mergedPins, splitPins, pinnedTaggedIdentity, stampedEventIds) = performPinning(
          nonCelebrityEvents,
          identityMatcherClassName,
          clusterSplitterClassName,
          ktRecords,
          identifyingAttributesBC,
          lastGenIdentityPinMap,
          nearNeighborFilename,
          nearNeighborFormat,
          kaTaggedIdentitiesBC,
          idMigrationConf,
          relationshipMigrationProcessor
        )

      if (emitStockMetricsEnabled) {
        if (lastGenIdentityPinMap != null && !lastGenIdentityPinMap.isEmpty) {
          emitStockMetric("lastGenCountPerPartition", "lastGenCountPerPartition", lastGenIdentityPinMap.count(), metricsMetadata)
        }
        else {
          emitStockMetric("lastGenCountPerPartition", "lastGenCountPerPartition", 0, metricsMetadata)
        }

        if (stampedIdentity != null && !stampedIdentity.isEmpty) {
          emitStockMetric("stampedIdentityCountPerPartition", "stampedIdentityCountPerPartition", stampedIdentity.count(), metricsMetadata)
        }
        else {
          emitStockMetric("stampedIdentityCountPerPartition", "stampedIdentityCountPerPartition", 0, metricsMetadata)
        }

        if (stampedEventIds != null && !stampedEventIds.isEmpty) {
          emitStockMetric("eventsPerPartition", "eventsPerPartition", stampedEventIds.count(), metricsMetadata)
        }
        else {
          emitStockMetric("eventsPerPartition", "eventsPerPartition", 0, metricsMetadata)
        }

        if (mergedPins != null && !mergedPins.isEmpty) {
          emitStockMetric("mergedPinCountPerPartition", "mergedPinCountPerPartition", mergedPins.count(), metricsMetadata)
        }
        else {
          emitStockMetric("mergedPinCountPerPartition", "mergedPinCountPerPartition", 0, metricsMetadata)
        }

        if (splitPins != null && !splitPins.isEmpty) {
          emitStockMetric("splitPinsCountPerPartition", "splitPinsCountPerPartition", splitPins.count(), metricsMetadata)
        }
        else {
          emitStockMetric("splitPinsCountPerPartition", "splitPinsCountPerPartition", 0, metricsMetadata)
        }

        if (stampedIdentity != null) {
          val x = if (mergedPins != null && !mergedPins.isEmpty) mergedPins.count() else 0
          val y = if (lastGenIdentityPinMap != null && !lastGenIdentityPinMap.isEmpty) lastGenIdentityPinMap.count() else 0
          emitStockMetric(MetricsConstants.Keys.NEW_PINS, MetricsConstants.Keys.NEW_PINS, stampedIdentity.count() - y - x, metricsMetadata)
        }
        else {
          emitStockMetric(MetricsConstants.Keys.NEW_PINS, MetricsConstants.Keys.NEW_PINS, 0, metricsMetadata)
        }
      }

      /*val algo = pinnedTaggedIdentity.max()(new Ordering[Tuple2[Long, TaggedIdentityWithEventId]]() {
        override def compare(x: (Long, TaggedIdentityWithEventId), y: (Long, TaggedIdentityWithEventId)): Int =
          Ordering[Long].compare(x._1, y._1)
      })

      val algo2 = stampedIdentity.max()(new Ordering[StampedIdentity]() {
        override def compare(x: (StampedIdentity), y: (StampedIdentity)): Int =
          Ordering[Long].compare(x.pin, y.pin)
      })*/

      if (stampingByHC) {
        saveStampedEventId(stampedEventIds, curatedEvents.getNumPartitions)
      }

      if (stampedEvents != null) {
        if (shouldSaveHeterogeneousCollection)
          saveHeterogeneousCollection(stampedEvents, hcFileName, appContext.sparkSession)
        else
          pinningIOUtils.saveStampedEvents(stampedEvents, pinnedFilename, appContext.sparkSession)
        val contextPinMapFilename = newContextPinMapFilename(pinnedFilename)
        saveContextPinMap(stampedEvents, contextPinMapFilename)
      }

      val identityPinMapFilename = newIdentityPinMapFilename(pinnedFilename)
      val stampedIdentityFilename = newStampedIdentityFilename(pinnedFilename)
      if (saveStampedIdentityState) {
        val identityPinMap = getIdentityPinMap(pinnedTaggedIdentity, identifyingAttributesBC)
        saveIdentityPinMap(identityPinMap, identityPinMapFilename)

        val stampedIdentityFormat = SparkPinner.getStampedIdentityOutputFormat(oxygenConf)
        SparkPinner.saveStampedIdentity(
          oxygenIO, appContext.sparkSession, stampedIdentity,
          stampedIdentityFilename, stampedIdentityFormat
        )
      }

      if (mergedPins != null && !mergedPins.isEmpty) {
        val mergedPinFilename = newMergedPinFilename(pinnedFilename)
        saveMergedPin(mergedPins, mergedPinFilename)
      }

      if (splitPins != null && !splitPins.isEmpty) {
        val splitPinFilename = newSplitPinFilename(pinnedFilename)
        //  saveSplitPin(splitPins, splitPinFilename)
      }

      /**
       * Processing VLE events
       */
      val celebrityEvents = if (celebrityIds.nonEmpty) filterVleEvents(curatedEvents, celebrityIds) else spark.sparkContext.emptyRDD[TaggedCuratedEventWithMap]
      celebrityEvents.persist(StorageLevel.DISK_ONLY)
      if (!celebrityEvents.isEmpty) {
        val celebrityFilename = newCelebrityFilename(pinnedFilename)
        implicit val taggedCuratedEventEncoder: Encoder[TaggedCuratedEventWithMap] = org.apache.spark.sql.Encoders.kryo[TaggedCuratedEventWithMap]
        pinningIOUtils.saveCelebrityEvent(celebrityEvents.toDS, celebrityFilename)
      }
      celebrityEvents.unpersist()


      if (newDatasets.isDefined) {
        val value = newDatasets.get
        value.foreach(i => {
          orchestrationHelper.markDatasetAsSuccess(i.jobId(), i.datasetId(), i.location(), target = Option(i.target()))
        })
      }

      /**
       * Processing pin-index
       */
      if (pinIndexFile != "" && fileUtils.exists(identityPinMapFilename)) {
        logger.error("Processing pin-index.")
        val identityPinMapFileFormat = oxygenConf.oxygen.STATIC_PINNING_FORMAT
        processPinIndex(pinIndexFile, identityPinMapFilename, identityPinMapFileFormat)
      }

      logPinningProcessCompletedSuccessfully()
    } else {
      logger.info("PINNING OPERATION SKIPPED!!!!!. No new curated Files. To run this stage regardless set 'oxygen.application.processManagement.devMode' to true in config")
      // TODO: Configurable way to run pinning regardless of new curated file exists or not.
    }

    endService()
  }

  /**
   * Run function with timeout
   *
   * @param timeoutMs timeout limit in milliseconds
   * @return
   */
  def runWithTimeout[T](timeoutMs: Long)(f: => T) : Option[T] = {
    Some(Await.result(Future(f), timeoutMs milliseconds))
  }

  /**
   * Convert pinned output preserving schema
   *
   * @param pinnedLocation pinned location
   */
  override def convertPinnedOutputToPreserveSchema(pinnedLocation: String): Unit = {
    val oxygenIO = OxygenIOUtils(appContext.sparkSession)
    val stampedEvents: RDD[PinnedEventWithMap] = oxygenIO.read.pinnedEventWithMap(pinnedLocation, "avro")
    val stampedEventsAsJson = appContext.sparkSession.read.json(stampedEvents.map(x => ScalaJacksonUtil.toJson(x).get))
    stampedEventsAsJson.write.format("avro").save(pinnedLocation + "-withSchema")
  }


  /**
   * Perform pinning
   *
   * @param curatedEvents curated events RDD
   * @param identityMatcherClassName identityMatcher class name
   * @param clusterSplitterClassName clusterSplitter class name
   * @param ktRecords keep together RDD
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @param lastGenIdentityPinMap identity-pin map of the reference generation RDD
   * @param nearNeighborFilename near-neighbor filename
   * @param nearNeighborFormat near-neighbor format
   * @param kaTaggedIdentitiesBC broadcasted keep apart identities
   * @param idMigrationConf identity migration configuration
   * @param relationshipMigrationProcessor relationship migration processor object
   * @return stampedEvents, stampedIdentities, mergedPins, splitPins, pinnedTaggedIdentity, flattenedStampedEventIds
   */
  private[oxygen] def performPinning(
                                       curatedEvents: RDD[TaggedCuratedEventWithMap],
                                       identityMatcherClassName: String,
                                       clusterSplitterClassName: String,
                                       ktRecords: RDD[String],
                                       identifyingAttributesBC: Broadcast[Array[String]],
                                       lastGenIdentityPinMap: RDD[PinnedTaggedIdentity],
                                       nearNeighborFilename: String,
                                       nearNeighborFormat: String,
                                       kaTaggedIdentitiesBC: Broadcast[List[List[TaggedIdentity]]],
                                       idMigrationConf: IdMigrationConf,
                                       relationshipMigrationProcessor: RelationshipMigrationProcessor
                                     ): (
    RDD[PinnedEventWithMap],
    RDD[StampedIdentity],
    RDD[MergedPin],
    RDD[SplitPin],
    RDD[(Long, TaggedIdentityWithEventId)],
    RDD[(String, Long)]
  ) = {
    /**
     * Build a list of unique identities and their associated event ids - identityEventMaps.
     */
    if (enableProfiling) {
      val count = curatedEvents.count
      logger.debug("Count of curated records: {} ", count)
      if (oxygenConf.oxygen.LAMBDA_TEST_MODE) addServiceInfoMetric("curatedRecords", count)
    }
    val taggedIdentityWithEventIds = if (lastGenIdentityPinMap != null) {
      getIdentityEventMapsWithPin(curatedEvents, lastGenIdentityPinMap, identifyingAttributesBC)
    } else
      getIdentityEventMaps(curatedEvents, identifyingAttributesBC)

    taggedIdentityWithEventIds.persist(StorageLevel.DISK_ONLY)

    if (enableProfiling) {
      val count = taggedIdentityWithEventIds.count
      logger.debug("Count of unique identity: {}", count)
      if (oxygenConf.oxygen.LAMBDA_TEST_MODE) addServiceInfoMetric("uniqueIdentity", count)
    }

    val identityMatcher = Class.forName(identityMatcherClassName).getConstructor().newInstance().asInstanceOf[IdentityMatcher]
    val clusterSplitter = if (clusterSplitterClassName != null && clusterSplitterClassName != "")
      Class.forName(clusterSplitterClassName).getConstructor().newInstance().asInstanceOf[ClusterSplitter]
    else null

    /**
     * Find potential matches by reducing on near neighbor keys
     */
    val potentialMatchedIdentities = if (groupSSR) {
      taggedIdentityWithEventIds.flatMap(x => {
        val identityId = x._1
        val identityEventMap = x._2
        val taggedIdentity = identityEventMap.taggedIdentity

        val nearNeighborKeys = identityMatcher.nearNeighborKeys(taggedIdentity)
        nearNeighborKeys.map(nearNeighborKey => {
          (nearNeighborKey, ListBuffer((identityId, taggedIdentity)))
        })
      })
        .filter(_._1 != "")
        .reduceByKey(_ ++ _)
    } else {
      taggedIdentityWithEventIds.flatMap(x => {
        val identityId = x._1
        val identityEventMap = x._2
        val taggedIdentity = identityEventMap.taggedIdentity

        val nearNeighborKeys = identityMatcher.nearNeighborKeys(taggedIdentity)
        nearNeighborKeys.map(nearNeighborKey => {
          (nearNeighborKey, ListBuffer((identityId, taggedIdentity)))
        })
      })
        .reduceByKey(_ ++ _)
    }
      .persist(StorageLevel.DISK_ONLY)

    potentialMatchedIdentities.name = "potentialMatchedIdentities"

    if (enableProfiling) {
      val count = potentialMatchedIdentities.count
      logger.debug("Count of potentialMatchedIdentities: {}", count)
      saveBlockSize(potentialMatchedIdentities)
      if (oxygenConf.oxygen.LAMBDA_TEST_MODE) addServiceInfoMetric("potentialMatchedIdentities", count)

    }

    if (saveNearNeighborState) {
      SparkPinner.saveNearNeighbors(
        oxygenIO, appContext.sparkSession,
        SparkPinner.convertNearNeighborsForSaving(potentialMatchedIdentities),
        nearNeighborFilename, nearNeighborFormat
      )
    }

    /**
     * Find exact matches for each near neighbor key
     */
    val locallyMatchedIdentities = potentialMatchedIdentities.mapPartitions(iter => {
      val res = ListBuffer[(List[List[Long]],List[String])]()
      iter.foreach(x => {
        val identities = x._2.toArray
        val blockSize = identities.length
        if (blockSize < nearNeighborBlockSize) {
          val matchedIdentities = if (enableDoesMatchProfiling) getExactMatchesWithProfile(identities, identityMatcher)
          else getExactMatches(identities, identityMatcher)
          res += matchedIdentities
        } else println("*** Ignored block: " + x._1)
      })
      res.toList.iterator
    })

    val locallyMatchedIdentities2 = locallyMatchedIdentities.flatMap(x => x._1)

    locallyMatchedIdentities2.name = "locallyMatchedIdentities"
    if (enableProfiling) {
      saveAttributesMatchedClusterSize(locallyMatchedIdentities2)
    }

    if (enableDoesMatchProfiling) {
      saveBlockDoesMatchProfileData(locallyMatchedIdentities.flatMap(x=>x._2))
    }

    /**
     * Process keep together
     */
    val locallyMatchedIdentitiesWithKTComponents = if (ktRecords != null) {
      val ktComponents = getKeepTogetherComponents(ktRecords, identifyingAttributesBC)
      locallyMatchedIdentities2.union(ktComponents)
    } else {
      locallyMatchedIdentities2
    }

    /**
     * Process identity migration
     */
    val locallyMatchedIdentitiesWithKTComponentsWithIdMigration = if (isValidIdMigrationConf(idMigrationConf, relationshipMigrationProcessor)) {
      logger.error("Obtaining migration identities")
      val migrationEvents = getIdMigrationEvents(idMigrationConf, curatedEvents).persist(StorageLevel.DISK_ONLY)
      val identityMigrationFieldsBC = spark.sparkContext.broadcast(idMigrationConf.identityMigrationFields)
      val idMigrationComponents = getIdMigrationComponents(migrationEvents, identityMigrationFieldsBC, identifyingAttributesBC, relationshipMigrationProcessor)
        .persist(StorageLevel.DISK_ONLY)
      if (!idMigrationComponents.isEmpty) {
        logger.error("Valid identity migration clusters: " + idMigrationComponents.count.toString)
        locallyMatchedIdentitiesWithKTComponents.union(idMigrationComponents)
      }
      else {
        logger.error("No valid identity migration clusters.")
        locallyMatchedIdentitiesWithKTComponents
      }
    } else {
      logger.error("No valid identity migration clusters.")
      locallyMatchedIdentitiesWithKTComponents
    }

    /**
     * Union with SSR-based clusters
     */
    val unionedIdentities = if (groupSSR) {
      logger.error("Creating SSR-based clusters.")
      val ssrMatchedIdentities = taggedIdentityWithEventIds
        .map(v => {
          val identityId = v._1
          val taggedIdentityWithEventIds = v._2
          val taggedIdentity = taggedIdentityWithEventIds.taggedIdentity
          val defaultNNKey = identityMatcher.defaultNearNeighborKeys(taggedIdentity).last
          (defaultNNKey, List(identityId))
        })
        .reduceByKey(_ ++ _)
        .map(_._2)
      locallyMatchedIdentitiesWithKTComponentsWithIdMigration.union(ssrMatchedIdentities)
    } else {
      locallyMatchedIdentitiesWithKTComponentsWithIdMigration
    }

    /**
     * Build edges and vertices for components
     */
    val edges = unionedIdentities
      .filter(_.size > 1)
      .flatMap(list => list.indices.tail.map(i => list(i - 1) -> list(i)))
      .toDF("src", "dst")
    val vertices = taggedIdentityWithEventIds.map(_._1).toDF("id").dropDuplicates()
    val graph = GraphFrame(vertices, edges)

    /**
     * Run connected components to merge the local matches
     */
    val pinDir = oxygenConf.oxygen.PINNED_DIRECTORY
    val checkpointDir = pinDir.stripSuffix("/").split("/").dropRight(1).mkString("/") + "/" + "checkpoint-cc-" + timestamp
    spark.sparkContext.setCheckpointDir(checkpointDir)

    val ccTimeout = runWithTimeout(connectedComponentsTimeout) {
      logger.error("Connected component started")
      graph.connectedComponents.run().as[(Long, Long)].rdd
    }
    val ccFailed = if (ccTimeout == None) true else false

    if (!ccFailed) {
      /**
       * Join with the connected component output with the event Ids and stamp with pin.
       */
      val cc = ccTimeout.get
      logger.error("Connected component completed")

      if (lastGenIdentityPinMap != null) {
        val empty = mutable.ListBuffer[TaggedIdentityWithEventId]()
        val matchedIdentities = cc.join(taggedIdentityWithEventIds)
          .map(x => {
            val minIdentityId = x._2._1
            val taggedIdentityWithEventId = x._2._2
            (minIdentityId, taggedIdentityWithEventId)
          })
          .aggregateByKey(empty)(
            (lb, v) => lb += v,
            (lb1, lb2) => lb1 ++= lb2)

        /**
         * Apply clusterSplitter
         */
        val matchedIdentitiesAfterSplitting = if (clusterSplitter != null) {
          matchedIdentities.map(x => {
            val minIdentityId = x._1
            val taggedIdentitiesWithEventId = x._2
            val taggedIdentitiesWithEventIdMap = mutable.HashMap[Long, TaggedIdentityWithEventId]()
            val taggedIdentities = taggedIdentitiesWithEventId.map(y => {
              val taggedIdentity = y.taggedIdentity
              taggedIdentitiesWithEventIdMap.put(taggedIdentity.identity.identityId, y)
              taggedIdentity
            })
            val splittedTaggedIdentities = clusterSplitter.splitCluster(taggedIdentities.toList, identityMatcher)

            val splittedTaggedIdentitiesWithEventIds = splittedTaggedIdentities.map(x => x.map(x => taggedIdentitiesWithEventIdMap(x.identity.identityId)))
            val splittedEventIds = splittedTaggedIdentitiesWithEventIds.map(x => (minIdentityId, x))
            splittedEventIds
          }).flatMap(x => x )
        } else {
          matchedIdentities.map(v => (v._1, v._2.toList))
        }

        /**
         * Process keep apart
         */
        val matchedIdentitiesAfterKA = if (!kaTaggedIdentitiesBC.value.isEmpty) {
          logger.error("Conducting Keep Apart...")
          kaSplitComponents(matchedIdentitiesAfterSplitting, kaTaggedIdentitiesBC, identityMatcher)
        } else {
          logger.error("No valid keep apart condition.")
          matchedIdentitiesAfterSplitting.map(v => v._2)
        }

        /**
         * Split/merge pins
         */
        val stampedEventIdsWithMergedPins = matchedIdentitiesAfterKA.mapPartitionsWithIndex((partitionIndex, iter) => {
          val vlePins = getVlePins
          val stampedEventIdsWithMergedPins = iter.toList.map(x => {
            val taggedIdentityWithEventIds = x
            val identityGroups = mutable.HashMap[Long, ListBuffer[TaggedIdentityWithEventId]]()
            taggedIdentityWithEventIds.map(y => {
              val pin = y.pin
              identityGroups.getOrElseUpdate(pin, ListBuffer[TaggedIdentityWithEventId]()) += y
            })
            val pins = identityGroups.keys.toList

            if (pins.length == 1 && pins.last == -1L) {
              // New identity
              val newPin = PinGenerator.createPin(partitionIndex, pinningVleRange, vlePins)
              val newTaggedIdentityWithEventIds = taggedIdentityWithEventIds.map(y => {
                TaggedIdentityWithEventId(y.taggedIdentity, y.eventIds, newPin)
              })
              ((newPin, newTaggedIdentityWithEventIds), null)
            } else if (pins.length == 1) {
              // existing identity
              val existingPin = pins.last
              val stampedEventId = StampedEventId(existingPin, taggedIdentityWithEventIds.flatMap(y => y.eventIds))
              ((existingPin, taggedIdentityWithEventIds), null)
            } else {
              // Pin Merge case - more than one pin.
              // Use anyone of them.
              // If after filtering new records length is one then new events got the existing pin
              val filteredPins = pins.filter(_ != -1L)
              val mergedPin = {
                if (filteredPins.length > 1) MergedPin(pins.filter(_ != -1L)) else null
              }
              val anyExistingPin = filteredPins.last
              val newTaggedIdentityWithEventIds = taggedIdentityWithEventIds.map(y => {
                TaggedIdentityWithEventId(y.taggedIdentity, y.eventIds, anyExistingPin)
              })
              ((anyExistingPin, newTaggedIdentityWithEventIds), mergedPin)
            }
          })
          stampedEventIdsWithMergedPins.toIterator
        })

        val stampedTaggedIdentityWithEventIds = stampedEventIdsWithMergedPins.map(x => x._1)
        val mergedPins = stampedEventIdsWithMergedPins.map(x => x._2).filter(_ != null)
        val (splitStampedTaggedIdentity, splitPins) = findPinSplits1(stampedTaggedIdentityWithEventIds)

        val flattenedStampedEventIds = splitStampedTaggedIdentity.flatMap(x => x.eventIds.map(y => (y, x.pin)))

        val pinnedTaggedIdentity = splitStampedTaggedIdentity.map(x => (x.pin, x))
        val stampedEvents = if (!stampingByHC) performStamping(flattenedStampedEventIds, curatedEvents) else null
        val stampedIdentities = splitStampedTaggedIdentity.map(x => StampedIdentity(x.taggedIdentity.identity.identityId, x.pin))
        if (stampingByHC)
          (stampedEvents, stampedIdentities, mergedPins, splitPins, pinnedTaggedIdentity, flattenedStampedEventIds)
        else
          (stampedEvents, stampedIdentities, mergedPins, splitPins, pinnedTaggedIdentity, null)

      } else {

        val empty = mutable.ListBuffer[TaggedIdentityWithEventId]()
        val matchedIdentities = cc.join(taggedIdentityWithEventIds)
          .map(x => {
            val minIdentityId = x._2._1
            val taggedIdentityWithEventId = x._2._2
            (minIdentityId, taggedIdentityWithEventId)
          })
          .aggregateByKey(empty)(
            (lb, v) => lb += v,
            (lb1, lb2) => lb1 ++= lb2)

        /**
         * Apply clusterSplitter
         */
        val matchedIdentitiesAfterSplitting = if (clusterSplitter != null) {
          matchedIdentities.map(x => {
            val minIdentityId = x._1
            val taggedIdentitiesWithEventId = x._2
            val taggedIdentitiesWithEventIdMap = mutable.HashMap[Long, TaggedIdentityWithEventId]()
            val taggedIdentities = taggedIdentitiesWithEventId.map(y => {
              val taggedIdentity = y.taggedIdentity
              taggedIdentitiesWithEventIdMap.put(taggedIdentity.identity.identityId, y)
              taggedIdentity
            })
            val splittedTaggedIdentities = clusterSplitter.splitCluster(taggedIdentities.toList, identityMatcher)

            val splittedTaggedIdentitiesWithEventIds = splittedTaggedIdentities.map(x => x.map(x => taggedIdentitiesWithEventIdMap(x.identity.identityId)))
            splittedTaggedIdentitiesWithEventIds.map(x => (minIdentityId, x /*.map(x => x.eventIds.toList)*/ ))
          }).flatMap(x => x)
        }
        else {
          matchedIdentities.map(x => {
            val minIdentityId = x._1
            val identityEventMap = x._2
            (minIdentityId, identityEventMap.toList /*.map(y => y.eventIds.toList)*/ )
          })
        }

        /**
         * Process keep apart
         */
        val matchedIdentitiesAfterKA = if (!kaTaggedIdentitiesBC.value.isEmpty) {
          logger.error("Conducting Keep Apart...")
          kaSplitComponents(matchedIdentitiesAfterSplitting, kaTaggedIdentitiesBC, identityMatcher)
        } else {
          logger.error("No valid keep apart conditions.")
          matchedIdentitiesAfterSplitting.map(v => v._2)
        }

        /**
         * Assign pins
         */
        val pinnedTaggedIdentity = matchedIdentitiesAfterKA
          .zipWithUniqueId
          .flatMap(x => {
            val pin = (x._2 + pinningVleRange)
            val taggedIdentityWithEventIds = x._1
            taggedIdentityWithEventIds.map(y => (pin, y))
          }).persist(StorageLevel.DISK_ONLY)

        val stampedIdentities = pinnedTaggedIdentity.map(x => {
          val identityId = x._2.taggedIdentity.identity.identityId
          val pin = x._1
          StampedIdentity(identityId, pin)
        })

        val flattenedStampedEventIds = pinnedTaggedIdentity.map(x => {
          val eventIds = x._2.eventIds
          val pin = x._1
          eventIds.map(eventId => (eventId, pin))
        })
          .flatMap(x => x)

        val stampedEvents = if (!stampingByHC) performStamping(flattenedStampedEventIds, curatedEvents) else null
        if (stampingByHC)
          (stampedEvents, stampedIdentities, null, null, pinnedTaggedIdentity, flattenedStampedEventIds)
        else
          (stampedEvents, stampedIdentities, null, null, pinnedTaggedIdentity, null)
      }

    } else {
      logger.error("Connected component failed. Cannot perform pinning.")
      (null.asInstanceOf[RDD[PinnedEventWithMap]], null.asInstanceOf[RDD[StampedIdentity]], null, null, null, null)
    }
  }

  // TODO: Write tests for and refactor performPinning and performSingleBlockingKeyPinning to remove duplication.
  // Currently there aren't sufficient unit tests in place to
  // validate existing functionality, so these need to be implemented before
  // any refactoring can happen.

  /**
   * Save flattened stamped eventId
   *
   * @param stampedEventIds stamped events RDD
   */
  def saveFlattenedStampedEventId(stampedEventIds: RDD[(Long, Long)]) = {
    val filename = pinnedDir + File.separator + timestamp + "-pin-stamped"
    stampedEventIds.map(x => {
      x._1 + ":" + x._2
    }).saveAsTextFile(filename)
  }

  /**
   * Perform stamping
   *
   * @param stampedEventIds stamped events RDD
   * @param curatedRecords curated records RDD
   * @return stamped events RDD
   */
  private[oxygen] def performStamping(stampedEventIds: RDD[(String, Long)], curatedRecords: RDD[TaggedCuratedEventWithMap]): RDD[PinnedEventWithMap] = {

    val allEvents = curatedRecords.map(rec => {
      val eventId = rec.eventId
      (eventId, rec)
    })

    /**
     * Join stamped event ids to the original events
     *
     * TODO: Try HashPartitioner before the join and compare the performance
     * https://stackoverflow.com/questions/31392261/how-can-i-efficiently-join-a-large-rdd-to-a-very-large-rdd-in-spark
     */
    val stampedEvents = stampedEventIds
      .join(allEvents)
      .map(x => PinnedEventWithMap(x._2._1, x._2._2))
    stampedEvents
  }

  /**
   * Filter VLE Events
   *
   * @param events curated events RDD
   * @return VLE events RDD
   */
  private[oxygen] def filterVleEvents(events: RDD[TaggedCuratedEventWithMap],
                                             celebrityIds: Map[String, VleRecord]): RDD[TaggedCuratedEventWithMap] = {
    events.filter(r => {
      val isVle = vleManagerBC.value.get.isVle(r)
      isVle
    })
  }

  /**
   * Filter non-VLE Events
   *
   * @param events curated events RDD
   * @return non-VLE events RDD
   */
  private[oxygen] def filterNoneVleEvents(events: RDD[TaggedCuratedEventWithMap],
                                       celebrityIds: Map[String, VleRecord]): RDD[TaggedCuratedEventWithMap] = {
    events.filter(r => {
      val isVle = vleManagerBC.value.get.isVle(r)
      !isVle
    })
  }

  /**
   * Filter large blocks
   *
   * @param blocks potential matched identities
   * @param threshold identityMatcher.celebrityIdentityThreshold
   * @return remainingBlocks, bigBlocks
   */
  private[oxygen] def filterLargeBlocks(blocks: RDD[(String, ListBuffer[TaggedIdentityWithEventId])],
                                         threshold: Int): (RDD[(String, ListBuffer[TaggedIdentityWithEventId])], RDD[(String, ListBuffer[TaggedIdentityWithEventId])]) = {
    val bigBlocks = blocks.filter(x => x._2.length >= threshold)
    val remainingBlocks = blocks.filter(x => x._2.length < threshold)

    (remainingBlocks, bigBlocks)
  }

  /**
   * Match big blocks
   *
   * @param blocks big blocks
   * @param matchingKeysForLargeCluster identityMatcher.matchingKeysForCelebrity
   * @return matchedIdentities
   */
  private[oxygen] def matchBigBlocks(blocks: RDD[(String, ListBuffer[TaggedIdentityWithEventId])],
                                      matchingKeysForLargeCluster: (TaggedIdentity) => List[String]): RDD[List[TaggedIdentityWithEventId]] = {
    logger.debug("Matching the big blocks")
    val empty = mutable.ListBuffer[TaggedIdentityWithEventId]()
    val matchedIdentitiesFlattened = blocks.flatMap(c => {
      val taggedIdentityWithEventIds = c._2
      c._2.map(x => {
        val keys = matchingKeysForLargeCluster(x.taggedIdentity).mkString(":")
        (keys, x)
      })
    })
      .aggregateByKey(empty)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2
      )

    val nullBlocks = matchedIdentitiesFlattened.filter(x => x._1.replace(":", "").trim.isEmpty)
    val remainingBlocks = matchedIdentitiesFlattened.filter(x => !x._1.replace(":", "").trim.isEmpty)

    val remainingBlocksMatchedIdentities = remainingBlocks.map(x => x._2.toList)
    val nullBlocksMatchedIdentities = nullBlocks.map(x => {
      val key = x._1
      val indElements = x._2.combinations(1)
      indElements.map(x => (key, x))
    }).flatMap(x => x).map(x => x._2.toList)

    val matchedIdentities = remainingBlocksMatchedIdentities.union(nullBlocksMatchedIdentities)

    matchedIdentities
  }

  private[oxygen] def findPinSplits(stampedEventIds: RDD[StampedEventId]): (RDD[StampedEventId], RDD[SplitPin]) = {
    val empty = mutable.ListBuffer[StampedEventId]()
    val splittedStampedEventIdsWithSplitPins = stampedEventIds.map(x => {
      (x.pin, x)
    }).aggregateByKey(empty)(
      (lb, v) => lb += v,
      (lb1, lb2) => lb1 ++= lb2
    ).mapPartitionsWithIndex((partitionIndex, iter) => {
      val stampedEventIdsWithSplitPins = iter.toList.map(x => {
        val splittedPin = x._1
        val splittedIdentities = x._2
        if (x._2.length > 1) {
          // Split happened.  generate new pins
          val stampedEventIdsWithNewPins = splittedIdentities.map(y => {
            val newPin = PinGenerator.createPin(partitionIndex,pinningVleRange)
            (newPin, StampedEventId(newPin, y.eventIds))
          }).toList

          val newPins = stampedEventIdsWithNewPins.map(y => y._1)
          val splittedEventIds = stampedEventIdsWithNewPins.map(y => y._2)
          (splittedEventIds, SplitPin(splittedPin, newPins))
        } else {
          (splittedIdentities.toList, null)
        }
      })
      stampedEventIdsWithSplitPins.toIterator
    })
    val splittedStampedEventIds = splittedStampedEventIdsWithSplitPins.flatMap(x => x._1)
    val splittedPins = splittedStampedEventIdsWithSplitPins.filter(x => x._2 != null).map(x => x._2)

    (splittedStampedEventIds, splittedPins)

  }

  /**
   * Find split pins
   *
   * @param identitiesWithPin identities with pin RDD
   * @return taggedIdentityWithEventIds, splittedPins
   */
  private[oxygen] def findPinSplits1(identitiesWithPin: RDD[(Long, List[TaggedIdentityWithEventId])]): (RDD[TaggedIdentityWithEventId], RDD[SplitPin]) = {
    val empty = mutable.ListBuffer[List[TaggedIdentityWithEventId]]()
    val taggedIdentityWithEventIdWithSplitPins = identitiesWithPin
      .aggregateByKey(empty)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2
      ).mapPartitionsWithIndex((partitionIndex, iter) => {
      val vlePins = getVlePins
      val taggedIdentityWithEventIdsWithSplitPins = iter.toList.map(x => {
        val pin = x._1
        val taggedIdentityWithEventId = x._2
        if (x._2.length > 1) {
          // Split happened.  generate new pins
          val taggedIdentityWithEventIdWithNewPins = taggedIdentityWithEventId.flatMap(y => {
            val newPin = PinGenerator.createPin(partitionIndex,pinningVleRange, vlePins)
            y.map(z => (newPin, TaggedIdentityWithEventId(z.taggedIdentity, z.eventIds, newPin)))
            //(newPin, TaggedIdentityWithEventId(y.taggedIdentity, y.eventIds, newPin))
          }).toList

          val newPins = taggedIdentityWithEventIdWithNewPins.map(y => y._1)
          val splittedPinnedTaggedIdentityWithEventId = taggedIdentityWithEventIdWithNewPins.map(y => y._2)
          (splittedPinnedTaggedIdentityWithEventId, SplitPin(pin, newPins))
        } else {
          (taggedIdentityWithEventId.flatten.toList, null)
        }
      })
      taggedIdentityWithEventIdsWithSplitPins.toIterator
    })
    val taggedIdentityWithEventIds = taggedIdentityWithEventIdWithSplitPins.flatMap(x => x._1)
    val splittedPins = taggedIdentityWithEventIdWithSplitPins.filter(x => x._2 != null).map(x => x._2)

    (taggedIdentityWithEventIds, splittedPins)
  }

//  private def getSchemaForAvro(dictionaryFile: DomainDictionaryInternal): Schema = {
//
//    //Schema Element
//    val schemaElement = CuratedEventAvroSchemaGenerator(dictionaryFile).generateSchema
//
//    //Avro Schema
//    val avroSchema: Schema = AvroSchemaGenerator().generateAvroSchema(schemaElement)
//    avroSchema
//  }

  private def readCuratedRecordInAvro(files: List[String], avroSchema: Schema): RDD[String] = {

    val curatedEventsDF = appContext.sparkSession.read.format("avro")
      .option("avroSchema", avroSchema.toString)
      .load(files: _*)
    curatedEventsDF.toJSON.rdd
  }

  /**
   * Read curated events
   *
   * @param appContext Oxygen app context
   * @param files
   * @param readFormat
   * @return curated events
   */
  private[oxygen] def readCuratedEvents(appContext: SparkOxygenAppContext, files: List[String], readFormat: String): RDD[TaggedCuratedEventWithMap] = {
    val events = pinningIOUtils.readCuratedEvents
    copyOffsetFileForLambda(appContext)
    events
  }

  /**
   * Copy offset file for lambda
   *
   * @param appContext Oxygen app context
   */
  private def copyOffsetFileForLambda(appContext: SparkOxygenAppContext): Unit = {
    val offsetFileLocation = appContext.config.oxygen.stage.curation.get.paths.lambdaSync.offsetTarget
    if (fileUtils.exists(offsetFileLocation)) {
      Try(fileUtils.readFileContentAsString(offsetFileLocation)) match {
        case Success(offset) =>
          fileUtils.writeToFile(appContext.config.oxygen.generation.metadata.hfStartOffsetPath.get, offset)
        case Failure(exception) => logger.error("Lambda Offset not Written : " + exception.getMessage, exception)
      }
    } else {
      logger.warn("Lambda Offset not Written : No offset file found in location {}. This might cause StateGeneration job to miss HF data.", offsetFileLocation)
    }
  }

  /**
   * Create heterogeneous collection
   *
   * @param pinnedEvents pinned events RDD
   * @return heterogenous collection RDD
   */
  private[oxygen] def createHeterogeneousCollection(pinnedEvents: RDD[PinnedEventWithMap]): RDD[String] = {
    val empty = new mutable.ListBuffer[TaggedCuratedEventWithMap]()

    val hc = pinnedEvents.map(x => {
      val pin = x.pin
      val taggedCuratedEvent = x.taggedCuratedEvent
      (pin, taggedCuratedEvent)
    })
      .aggregateByKey(empty)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2)
      .map(x => ScalaJacksonUtil.toJson(HeterogeneousCollection(x._1, x._2.toArray)).get)

    hc
  }

  /**
   * Saves the pinnedEvents by creating heterogenous collection
   *
   * @param pinnedEvents pinned events RDD
   * @param filename filename
   */
  private[oxygen] def saveHeterogeneousCollection(pinnedEvents: RDD[PinnedEventWithMap], filename: String, sparkSession: SparkSession): Unit = {

    val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT
    val writeFormat = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.output.getOrElse(storageFormat)
    import appContext.sparkSession.implicits._
    val hc = createHeterogeneousCollection(pinnedEvents).toDS
    val hcAsJson = sparkSession.read.json(hc)

    if (writeFormat == Constants.FILE_AVRO_FORMAT) {
      sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
      hcAsJson.write.format("avro").save(filename)
    }
    else if (writeFormat == Constants.FILE_PARQUET_FORMAT) {
      hcAsJson.write.parquet(filename)
    }
    else {
      hcAsJson.write.json(filename)
    }
  }

  /**
   * Save context pin map
   *
   * @param pinnedEvents pinned events RDD
   * @param filename filename
   */
  private[oxygen] def saveContextPinMap(pinnedEvents: RDD[PinnedEventWithMap], filename: String): Unit = {
    val empty = ListBuffer[String]()
    val contextToPinRecs = pinnedEvents.map(x => {
      val taggedCuratedEvents = x.taggedCuratedEvent
      val pin = x.pin
      (pin, taggedCuratedEvents.subscriber + ":" + taggedCuratedEvents.subscriberRelation)

    })
      .aggregateByKey(empty)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2)
      .flatMap(x => {
        val pin = x._1
        val contexts = x._2.distinct
        contexts.map(context => new ContextPinMapEvent(context, pin).toString)
      })
    contextToPinRecs.saveAsTextFile(filename)
  }

  /**
   * Save merged pin
   *
   * @param mergedPin merged pin RDD
   * @param filename filename
   */
  private[oxygen] def saveMergedPin(mergedPin: RDD[MergedPin], filename: String): Unit = {
    mergedPin.saveAsTextFile(filename)
  }

  /**
   * Save split pin
   *
   * @param splitPin split pin RDD
   * @param filename filename
   */
  private[oxygen] def saveSplitPin(splitPin: RDD[SplitPin], filename: String): Unit = {
    splitPin.saveAsTextFile(filename)
  }

  /**
   * Save identity pin map
   *
   * @param identityPinMaps identity-pin maps RDD
   * @param filename filename
   */
  private[oxygen] def saveIdentityPinMap(identityPinMaps: RDD[PinnedTaggedIdentity], filename: String): Unit = {
    //val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT
    //val writeFormat = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.output.getOrElse(storageFormat)
    val writeFormat = oxygenConf.oxygen.STATIC_PINNING_FORMAT

    val recordsAsJson = identityPinMaps.map(x => {
      JsonUtilExperimental.toJson(x)
    })
    val recordsJsonDF = appContext.sparkSession.read.json(recordsAsJson.toDS)

    if (writeFormat == Constants.FILE_AVRO_FORMAT) {
      appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
      recordsJsonDF.write.format("avro").save(filename)
    }
    else if (writeFormat == Constants.FILE_PARQUET_FORMAT) {
      recordsJsonDF.write.parquet(filename)
    }
    else {
      recordsJsonDF.write.json(filename)
    }
  }

  /**
   * Save potentially matched block size
   *
   * @param potentialMatchedIdentities potential matched identities
   */
  private[oxygen] def saveBlockSize(potentialMatchedIdentities: RDD[(String, ListBuffer[(Long, TaggedIdentity)])]) = {
    val blockSize = potentialMatchedIdentities.map(x => {
      x._1 + "\t" + x._2.length
    })
    val filename = pinnedDir + File.separator + timestamp + "-profile-block-size-count"
    blockSize.saveAsTextFile(filename)
  }

  /**
   * Save potentially matched block size for single blocking key
   *
   * @param potentialMatchedIdentities potential matched identities
   */
  private[oxygen] def saveBlockSizeSingleBlockingKey(potentialMatchedIdentities: RDD[(String, ListBuffer[TaggedIdentityWithEventId])]) = {
    val blockSize = potentialMatchedIdentities.map(x => {
      x._1 + "\t" + x._2.length
    })
    val filename = pinnedDir + File.separator + timestamp + "-profile-block-size-count"
    blockSize.saveAsTextFile(filename)
  }

  /**
   * Save large block size
   *
   * @param potentialMatchedIdentities potential matched identities
   */
  private[oxygen] def saveBigBlockSingleBlockingKey(potentialMatchedIdentities: RDD[(String, ListBuffer[TaggedIdentityWithEventId])]) = {
    val blockSize = potentialMatchedIdentities.map(x => {
      x._1 + "\t" + x._2.length
    })
    val filename = pinnedDir + File.separator + timestamp + "-profile-large-block-size-count"
    blockSize.saveAsTextFile(filename)
  }

  /**
   * Save locally matched block size
   *
   * @param matchedClusters locally matched clusters RDD
   */
  private[oxygen] def saveAttributesMatchedClusterSize(matchedClusters: RDD[List[Long]]) = {
    val counts = matchedClusters.map(x => {
      x.length
    })
    val filename = pinnedDir + File.separator + timestamp + "-profile-attributes-matched-cluster-size"
    counts.saveAsTextFile(filename)
  }

  /**
   * Save locally matched block size for single block pinning
   *
   * @param matchedClusters locally matched clusters RDD
   */
  private[oxygen] def saveAttributesMatchedClusterSizeSingleBlock(matchedClusters: RDD[List[TaggedIdentityWithEventId]]) = {
    val counts = matchedClusters.map(x => {
      x.length
    })
    val filename = pinnedDir + File.separator + timestamp + "-profile-relations-matched-cluster-size"
    counts.saveAsTextFile(filename)
  }

  /**
   * Save pinned tagged identities
   *
   * @param pinnedTaggedIdentity pinned tagged identities
   */
  private[oxygen] def savePinnedTaggedIdentity(pinnedTaggedIdentity: RDD[(Long, TaggedIdentityWithEventId)]) = {
    val res = pinnedTaggedIdentity.map(x => {
      x._1 + "\t" + x._2.taggedIdentity.identity.identityId + "\t" + x._2.taggedIdentity.identity.getStringValue
    })

    val filename = pinnedDir + File.separator + timestamp + "-profile-pinned-tagged-identity"
    res.saveAsTextFile(filename)
  }

  /**
   * Save pinned eventIds
   *
   * @param pinnedEventId pinned eventIds
   */
  private[oxygen] def savePinnedEventId(pinnedEventId: RDD[(String, Long)]) = {
    val res = pinnedEventId.map(x => {
      x._1 + "\t" + x._2
    })

    val filename = pinnedDir + File.separator + timestamp + "-profile-pinned-event-id"
    res.saveAsTextFile(filename)
  }

  /**
   * Save stamped eventId
   *
   * @param flattenedStampedEventId flattened stamped eventId RDD
   * @param numPartitions number of partitions
   * @return
   */
  def saveStampedEventId(flattenedStampedEventId: RDD[(String, Long)], numPartitions: Int) = {
    val df = appContext.sparkSession.createDataFrame(flattenedStampedEventId).toDF("eventId", "pin")
    val dfPartition = if (numPartitions > 0) df.repartition(numPartitions, col("eventId")) else df
    oxygenIO.write.dataFrame(dfPartition, pinnedFilename + "-EID", oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.output.get)
  }

  /**
   * Applies exact match rules and return lists of identities that are equal
   * It applies union-find algorithm to return the matches.
   *
   * @param potentialMatches Array of (identityId, identity) that are potentially same.
   * @param identityMatcher identityMatcher object.
   * @return Lists of list id identityIds that are exact match
   */
  private[oxygen] def getExactMatches(potentialMatches: Array[(Long, TaggedIdentity)], identityMatcher: IdentityMatcher): (List[List[Long]],List[String]) = {

    val len = potentialMatches.length
    val uf = new UnionFind(len)

    var i = 0
    while (i < len) {
      var j = i + 1
      while (j < len) {
        if (identityMatcher.doesMatchWithProfiling(potentialMatches(i)._2,
          potentialMatches(j)._2)._1._1) uf.union(i, j)
        j = j + 1
      }
      i = i + 1
    }
    val allUnions = uf.getAllUnions(potentialMatches)

    (allUnions.map(x => x.map(y => y._1)),List.empty[String])
  }

  private[pinning] def getExactMatchesWithProfile(potentialMatches: Array[(Long, TaggedIdentity)], identityMatcher: IdentityMatcher): (List[List[Long]],List[String]) = {

    val len = potentialMatches.length
    val uf = new UnionFind(len)
    val profileLogs = ListBuffer[String]()
    var i = 0
    while (i < len) {
      var j = i + 1
      while (j < len) {
         val doesMatchWithProfileResult =  identityMatcher.doesMatchWithProfiling(potentialMatches(i)._2,potentialMatches(j)._2)
         val doesMatchResult = doesMatchWithProfileResult._1._1
         val profileData = doesMatchWithProfileResult._2
        profileLogs+= profileData
        if ( doesMatchResult ) uf.union(i, j)
        j = j + 1
      }
      i = i + 1
    }
    val allUnions = uf.getAllUnions(potentialMatches)


    (allUnions.map(x => x.map(y => y._1)),profileLogs.toList)
  }


  /**
   * Applies exact match rules and return lists of identities that are equal
   * It applies union-find algorithm to return the matches.
   *
   * @param potentialMatches Array of (identityId, identity) that are potentially same.
   * @param identityMatcher identityMatcher object.
   * @return Lists of list TaggedIdentityWithEventId that are exact match
   */
  private[oxygen] def getExactMatchesSingleBlockingKey(potentialMatches: Array[TaggedIdentityWithEventId],
                                                        identityMatcher: IdentityMatcher): List[List[TaggedIdentityWithEventId]] = {

    val len = potentialMatches.length
    val uf = new UnionFind(len)
    val profileLogs = ListBuffer[String]()
    var i = 0
    while (i < len) {
      var j = i + 1
      while (j < len) {
        val doesMatchWithProfileResult = identityMatcher.doesMatchWithProfiling(potentialMatches(i).taggedIdentity,potentialMatches(j).taggedIdentity)
        val doesMatchResult = doesMatchWithProfileResult._1._1
        val profileData = doesMatchWithProfileResult._2
        profileLogs+= profileData
        if (doesMatchResult) {
          uf.union(i, j)
        }
        j = j + 1
      }
      i = i + 1
    }

    if (enableDoesMatchProfiling) {
      if (!profileLogs.isEmpty) {
        val pprofileData = spark.sparkContext.parallelize(profileLogs.toList)
        saveBlockDoesMatchProfileData(pprofileData);
      }
    }
    val allUnions = uf.getAllUnions[TaggedIdentityWithEventId](potentialMatches)
    allUnions
  }


  //region start - keep together

  private[oxygen] override def retrieveKeepTogetherFiles = super.retrieveKeepTogetherFiles

  /**
   * Read keep together records
   *
   * @param files list of keep together files
   * @return keep together records RDD
   */
  private[oxygen] def readKeepTogetherRecords(files: List[String]): RDD[String] = {
    if (files.nonEmpty) {
      val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT
      val readFormat = oxygenConf.oxygen.PINNING_READ_WRITE_FORMAT.input.getOrElse(storageFormat)

      val ktRecords = if (readFormat == Constants.FILE_AVRO_FORMAT) {
        appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
        try {
          val ktRecordsDF = appContext.sparkSession.read.format("avro").load(files: _*)
          ktRecordsDF.toJSON.rdd
        }
        catch {
          case fne: FileNotFoundException => {
            appContext.sparkSession.emptyDataFrame.toJSON.rdd
          }
        }
      }
      else if (readFormat == Constants.FILE_PARQUET_FORMAT) {
        try {
          val ktRecordsDF = appContext.sparkSession.read.parquet(files: _*)
          ktRecordsDF.toJSON.rdd
        }
        catch {
          case fne: FileNotFoundException => {
            appContext.sparkSession.emptyDataFrame.toJSON.rdd
          }
        }
      }
      else {
        val ktRecordsDF = appContext.sparkSession.read.json(files: _*)
        ktRecordsDF.toJSON.rdd
      }
      ktRecords
    } else
      null
  }

  /**
   * Create keep together clusters
   *
   * @param ktRecords keep together records RDD
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @return keep together identityId clusters RDD
   */
  private[oxygen] def getKeepTogetherComponents(ktRecords: RDD[String], identifyingAttributesBC: Broadcast[Array[String]]): RDD[List[Long]] = {
    ktRecords
      .map(v => {
        val identities = ScalaJacksonUtil.fromJson[KtIdentities](v)
        if (identities.get != None) {
          identities.get.identities.map(attributes => {
            val identity = Identity.createIdentityWithIdentifyingAttributes(attributes, identifyingAttributesBC.value)
            identity.identityId
          })
        } else {
          List.empty[Long]
        }
      })
      .filter(v => !v.isEmpty && v.size > 1)
  }

  //region end - keep together

  /**
   * Get identity-pin maps
   *
   * @param pinnedTaggedIdentity pinned taggedIdentity RDD
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @return identity-pin maps RDD
   */
  private[oxygen] def getIdentityPinMap(pinnedTaggedIdentity: RDD[(Long, TaggedIdentityWithEventId)],
                                        identifyingAttributesBC: Broadcast[Array[String]]): RDD[PinnedTaggedIdentity] = {
    pinnedTaggedIdentity.map(x => PinnedTaggedIdentity(x._2.taggedIdentity, x._1))
  }

  /**
   * Get identity event maps with pin
   *
   * @param curatedEvents curated events RDD
   * @param identityPinMap identity-pin maps RDD
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @return identityEvents
   */
  private[oxygen] def getIdentityEventMapsWithPin(curatedEvents: RDD[TaggedCuratedEventWithMap],
                                                  identityPinMap: RDD[PinnedTaggedIdentity],
                                                  identifyingAttributesBC: Broadcast[Array[String]]): RDD[(Long, TaggedIdentityWithEventId)] = {

    val vlePins = getVlePins
    val identityPinMapFilteredByVle = if (!vlePins.isEmpty) identityPinMap.filter(x => !vlePins.contains(x.pin)) else identityPinMap
    val identityIdPinMap = identityPinMapFilteredByVle.map(x => {
      if (x.pin < pinningVleRange) {
        throw new Exception(s"Invalid Pin. Pin Must be  greater than PinningVleRange [0 - '${pinningVleRange}']. Set [reusePin] = false to recreate the pinId with a valid range")
      }
      (x.taggedIdentity.identity.identityId, (null.asInstanceOf[ListBuffer[String]], x.taggedIdentity, x.pin))
    })

    val empty = mutable.ListBuffer[(ListBuffer[String], TaggedIdentity, Long)]()

    val identityEvents = curatedEvents.mapPartitions(iter => {
      val uniqueIdentitiesMap = mutable.HashMap[Long, (ListBuffer[String], TaggedIdentity)]()
      iter.foreach(rec => {
        val eventId = rec.eventId
        val taggedIdentity = TaggedIdentity.createWithTaggedCuratedEventWithMap(rec, identifyingAttributesBC.value)
        val found = uniqueIdentitiesMap.get(taggedIdentity.identity.identityId).orNull
        if (found == null) {
          val arr = ListBuffer[String]()
          arr += eventId
          uniqueIdentitiesMap.put(taggedIdentity.identity.identityId, (arr, taggedIdentity))
        }
        else {
          found._1 += eventId
        }
      })

      val uniqueIdentities = ListBuffer[(Long, (ListBuffer[String], TaggedIdentity, Long))]()
      uniqueIdentitiesMap.foreach(kv => {
        val identityId = kv._1
        val eventIds = kv._2._1
        val taggedIdentity = kv._2._2
        uniqueIdentities += ((identityId, (eventIds, taggedIdentity, -1L)))
      })
      uniqueIdentities.toIterator
    })
      .union(identityIdPinMap)
      .aggregateByKey(empty)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2)
      .map(x => {
        val identityId = x._1
        val preGenIdentity = x._2.filter(y => y._3 != -1)
        val identities = x._2
        val eventIds = identities.filter(x => x._1 != null).flatMap(y => y._1)

        if (enableProfiling) {
          val h = mutable.HashSet[String]()
          identities.foreach(x => h.add(x._2.identity.getStringValue))
          if (h.size > 1) {
            logger.error("IdentityId HashConflict: " + h.mkString("|"))
          }
        }

        if (preGenIdentity.isEmpty) {
          if (enableProfiling) {
            logger.error(escapeJava("Found New TaggedIdentities: " + identities.last._2.toString))
          }
          (identityId, TaggedIdentityWithEventId(identities.last._2, eventIds.toList, -1L))
        }
        else
          (identityId, TaggedIdentityWithEventId(identities.last._2, eventIds.toList, preGenIdentity.last._3))
      })
    identityEvents
  }

  /**
   * Get identity event maps
   *
   * @param curatedEvents           curated events RDD
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @return identityEvents
   */
  private[oxygen] def getIdentityEventMaps(curatedRecords: RDD[TaggedCuratedEventWithMap],
                                           identifyingAttributesBC: Broadcast[Array[String]]): RDD[(Long, TaggedIdentityWithEventId)] = {

    val numPartition = (curatedRecords.getNumPartitions * repartitionFactor).toInt
    val empty = mutable.ListBuffer[(ListBuffer[String], TaggedIdentity)]()
    val identityEvents = curatedRecords.mapPartitions(iter => {
      val uniqueIdentitiesMap = mutable.HashMap[Long, (ListBuffer[String], TaggedIdentity)]()
      iter.foreach(rec => {
        val eventId = rec.eventId
        val taggedIdentity = TaggedIdentity.createWithTaggedCuratedEventWithMap(rec, identifyingAttributesBC.value)
        val found = uniqueIdentitiesMap.get(taggedIdentity.identity.identityId).orNull
        if (found == null) {
          val arr = ListBuffer[String]()
          arr += eventId
          uniqueIdentitiesMap.put(taggedIdentity.identity.identityId, (arr, taggedIdentity))
        }
        else {
          found._1 += eventId
        }
      })

      val uniqueIdentities = ListBuffer[(Long, (ListBuffer[String], TaggedIdentity))]()
      uniqueIdentitiesMap.foreach(kv => {
        val identityId = kv._1
        val eventIds = kv._2._1
        val taggedIdentity = kv._2._2
        uniqueIdentities += ((identityId, (eventIds, taggedIdentity)))
      })
      uniqueIdentities.toIterator
    })
      .aggregateByKey(empty, numPartition)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2)
      .map(x => {
        val identityId = x._1
        val identities = x._2
        if (enableProfiling) {
          val h = mutable.HashSet[String]()
          identities.foreach(x => h.add(x._2.identity.getStringValue))
          if (h.size > 1) {
            logger.error("IdentityId HashConflict: " + h.mkString("|"))
          }
        }
        val eventIds = identities.flatMap(y => y._1)
        (identityId, TaggedIdentityWithEventId(identities.last._2, eventIds.toList, -1L))
      })
    identityEvents
  }

  private[oxygen] def createIdentityPinMap(pinnedRecords: RDD[(Long, String)],
                                           identifyingAttributesBC: Broadcast[Array[String]]): RDD[PinnedTaggedIdentity] = {

    val empty = mutable.ListBuffer[PinnedTaggedIdentity]()
    val identityPinMaps = pinnedRecords.map(rec => {
      val pin = rec._1
      val taggedCuratedEvent = JsonUtils.fromJson(rec._2, classOf[TaggedCuratedEvent])
      val taggedIdentity = TaggedIdentity.createWithTaggedCuratedEvent(taggedCuratedEvent, identifyingAttributesBC.value)
      (taggedIdentity.identity.identityId, PinnedTaggedIdentity(taggedIdentity, pin))
    })
      .aggregateByKey(empty)(
        (lb, v) => lb += v,
        (lb1, lb2) => lb1 ++= lb2
      )
      .map(x => x._2.last)

    identityPinMaps
  }



  //region start - keep apart
  /**
   * Splits components based on Keep Apart
   * Convert keep apart files to keep apart identities
   *
   * @param identityClusters identity clusters
   * @param kaTaggedIdentitiesBC broadcasted keep apart identity clusters
   * @param identityMatcher identityMatcher object
   * @return split clusters RDD
   * @param kaTaggedIdentitiesRead keep apart dataframe
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @return keep apart identities RDD
   */
  def kaFilesToIdentities(kaTaggedIdentitiesRead: DataFrame, identifyingAttributesBC: Broadcast[Array[String]]): RDD[List[(Long, TaggedIdentity)]] = {
    kaTaggedIdentitiesRead.toJSON.rdd
      .map(v => {
        val identities = ScalaJacksonUtil.fromJson[KaIdentities](v)
        if (identities.get != None) {
          identities.get.identities.map(attribute => {
            val identity = Identity.createIdentityWithIdentifyingAttributes(attribute.attributes, identifyingAttributesBC.value)
            val taggedIdentity = TaggedIdentity(
              identity,
              identity.attributes.getOrElse("subscriber", "").toString,
              identity.attributes.getOrElse("subscriberRelation", "").toString
            )
            taggedIdentity
          })
        } else {
          List.empty[TaggedIdentity]
        }
      })
      .filter(v => !v.isEmpty && v.size > 1)
      .map(cluster => cluster.map(v => (v.identity.identityIdNoSSR, v)))
  }

  /**
   * Re-arrange keep apart identities by doing a connected component first to combine identities and remove duplicates
   *
   * @param kaTaggedIdentityListRead keep apart identities RDD
   * @return re-arranged keep apart identities RDD
   */
  def kaRearrangeIdentities(kaTaggedIdentityListRead: RDD[List[(Long, TaggedIdentity)]]): RDD[List[TaggedIdentity]] = {
    logger.error("Rearranged KA identities:")
    val identityIdTaggedIdentityMap = kaTaggedIdentityListRead.flatMap(v => v).distinct.cache
    val vertices = identityIdTaggedIdentityMap.map(_._1).toDF("id")
    val edges = kaTaggedIdentityListRead.map(_.map(_._1))
      .filter(_.size > 1)
      .flatMap(list => list.indices.tail.map(i => list(i - 1) -> list(i)))
      .toDF("src", "dst")
    val graph = GraphFrame(vertices, edges)
    val cc = graph.connectedComponents.setAlgorithm("graphx").run

    val ccClustersDf = cc.groupBy("component").agg(collect_list("id").alias("ids"))
      .select("ids").filter("size(ids) > 1")
    val ccCluster = ccClustersDf.as[List[Long]].rdd

    val identityIdTaggedIdentityMapBC = spark.sparkContext.broadcast(identityIdTaggedIdentityMap.collectAsMap())
    val taggedIdentityCluster = ccCluster.map(_.map(v => identityIdTaggedIdentityMapBC.value.getOrElse(v, TaggedIdentity(Identity(Map[String, Any]()), "", ""))))

    identityIdTaggedIdentityMap.unpersist(false)
    taggedIdentityCluster
  }

  /**
   * Read keep apart files
   *
   * @param kaFilesFolder           keep apart folder
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @return broadcasted keep apart identities RDD
   */
  def readKeepApartFiles(kaFilesFolder: String, identifyingAttributesBC: Broadcast[Array[String]]): Broadcast[List[List[TaggedIdentity]]] = {
    val kaTaggedIdentitiesRead = spark.read.option("encoding", "UTF-8").json(kaFilesFolder)
    val kaTaggedIdentities = if (!kaTaggedIdentitiesRead.isEmpty) {
      logger.error("Reading ka records...")
      val kaTaggedIdentityListRead = kaFilesToIdentities(kaTaggedIdentitiesRead, identifyingAttributesBC)
      kaRearrangeIdentities(kaTaggedIdentityListRead)
    } else {
      spark.sparkContext.emptyRDD[List[TaggedIdentity]]
    }
    val kaTaggedIdentitiesList = kaTaggedIdentities.collect.toList
//    kaTaggedIdentitiesList.foreach(l => logger.error("KA - " + l.map(_.identity.getStringValueNoSSR).mkString(", ")))
    spark.sparkContext.broadcast(kaTaggedIdentitiesList)
  }

  /**
   * Splits components based on Keep Apart
   *
   * @param identityClusters     identity clusters
   * @param kaTaggedIdentitiesBC broadcasted keep apart identity clusters
   * @param identityMatcher      identityMatcher object
   * @return split clusters RDD
   */
  def kaSplitComponents(identityClusters: RDD[(Long, List[TaggedIdentityWithEventId])],
                        kaTaggedIdentitiesBC: Broadcast[List[List[TaggedIdentity]]],
                        identityMatcher: IdentityMatcher
                       ): RDD[List[TaggedIdentityWithEventId]] = {
    identityClusters
      .flatMap(v => {
        val identityCluster = v._2
        val identityIds = identityCluster.map(_.taggedIdentity.identity.identityIdNoSSR).distinct
        val validKaTaggedIdentities = kaTaggedIdentitiesBC.value
          .map(row => {
            val kaIdentityIds = row.map(_.identity.identityIdNoSSR)
            (kaIdentityIds, row)
          })
          .filter(_._1.intersect(identityIds).size > 0)
          .map(_._2)

        val splitCluster = if (validKaTaggedIdentities.size > 0) {
//          logger.error("Splitting a cluster with KA - " + validKaTaggedIdentities.map(_.map(_.identity.getStringValueNoSSR).mkString(" <> ")))
          validKaTaggedIdentities.foldLeft(List(identityCluster))((x, y) => {
            val kaTaggedIdentities = y
            val splitClustersList = x.flatMap(taggedIdentityWithEventIdList => {
              taggedIdentityWithEventIdList.map(taggedIdentityWithEventId => {
                val taggedIdentity = taggedIdentityWithEventId.taggedIdentity
                val scoresAlongKaAxis = kaTaggedIdentities.map(kaTaggedIdentity => identityMatcher.doesMatchKA(kaTaggedIdentity, taggedIdentity))
                val kaIdentityIndex = scoresAlongKaAxis.indices.maxBy(scoresAlongKaAxis)
                (kaIdentityIndex, taggedIdentityWithEventId)
              })
                .groupBy(_._1).values.toList
                .map(_.map(_._2))
            })
            splitClustersList
          })
        } else {
          List(identityCluster)
        }
        splitCluster
      })
  }
  //region end - keep apart

  //region start - identity migration
  /**
   * Convert string to IdMigrationConf
   *
   * @param idMigrationConfStr idMigrationConf in string
   * @return idMigrationConf object
   */
  def convertStringToIdMigrationConf(idMigrationConfStr: String): IdMigrationConf = {
    ScalaJacksonUtil.fromJson[IdMigrationConf](idMigrationConfStr).getOrElse(emptyIdMigrationConf)
  }

  def emptyIdMigrationConf: IdMigrationConf = IdMigrationConf(Map.empty[String, Any], Array.empty[Map[String, String]])

  def lowerCaseTypes(typename: String): String = typename.toLowerCase + "s"

  /**
   * Check if idMigrationConf is valid
   *
   * @param idMigrationConf idMigrationConf object
   * @param relationshipMigrationProcessor relationshipMigrationProcessor object
   * @return true if idMigrationConf is valid
   */
  def isValidIdMigrationConf(idMigrationConf: IdMigrationConf, relationshipMigrationProcessor: RelationshipMigrationProcessor): Boolean = {
    (!idMigrationConf.identityMigrationFilter.isEmpty &&
      idMigrationConf.identityMigrationFilter.get("field") != None &&
      idMigrationConf.identityMigrationFilter.get("value") != None) &&
      ((!idMigrationConf.identityMigrationFields.isEmpty) || (relationshipMigrationProcessor != null))
  }

  /**
   * Get ID migration events from curated events by identityMigrationFilter
   *
   * @param idMigrationConf idMigrationConf object
   * @param curatedEvents curated events RDD
   * @return ID migration events RDD
   */
  def getIdMigrationEvents(idMigrationConf: IdMigrationConf, curatedEvents: RDD[TaggedCuratedEventWithMap]): RDD[TaggedCuratedEventWithMap] = {
    val imFilterField = idMigrationConf.identityMigrationFilter.get("field").get.toString
    val imFilterValue = idMigrationConf.identityMigrationFilter.get("value").get
    curatedEvents.filter(v => {
      val filterValueOption = v.curatedRecordJson.get(imFilterField)
      if (filterValueOption == null) false else v.curatedRecordJson.get(imFilterField) == imFilterValue
    })
  }

  /**
   * Get ID migration clusters.
   * Keep current and destination migration identities together.
   *
   * @param migrationEvents migration events RDD
   * @param identityMigrationFieldsBC broadcasted ID migration fields
   * @param identifyingAttributesBC broadcasted identifying attributes
   * @param relationshipMigrationProcessor relationshipMigrationProcessor object
   * @return ID migration events RDD
   */
  def getIdMigrationComponents(migrationEvents: RDD[TaggedCuratedEventWithMap],
                               identityMigrationFieldsBC: Broadcast[Array[Map[String, String]]],
                               identifyingAttributesBC: Broadcast[Array[String]],
                               relationshipMigrationProcessor: RelationshipMigrationProcessor): RDD[List[Long]] = {
    val relationshipMigrationProcessorBC = spark.sparkContext.broadcast(relationshipMigrationProcessor)

    if (!migrationEvents.isEmpty) {
      migrationEvents.map(event => {
        val subscriberMap = "subscriber" -> event.subscriber
        val subscriberRelationMap = "subscriberRelation" -> event.subscriberRelation

        val (destinationSubscriber, destinationSubscriberRelation) = if (relationshipMigrationProcessorBC.value == null) (event.subscriber, event.subscriberRelation) else relationshipMigrationProcessorBC.value.getDestination(event.curatedRecordJson)
        val destinationSubscriberMap = "subscriber" -> destinationSubscriber
        val destinationSubscriberRelationMap = "subscriberRelation" -> destinationSubscriberRelation

        val migrationFields = identityMigrationFieldsBC.value.map(_.get("field").get)
        val unspecifiedFields = identifyingAttributesBC.value.filter(!migrationFields.contains(_))
        val unspecifiedAttributes = unspecifiedFields.map(field => {
          field -> event.curatedRecordJson.get(field)
        })
          .filter(v => v._2 != null)
          .toMap

        val eventKeys = event.curatedRecordJson.keys.toList

        val destinationAttributes = identityMigrationFieldsBC.value.map(v => {
          val field = v.get("field").get
          if (eventKeys.contains(field)) {
            val destinationValue = event.curatedRecordJson.getOrElse(field, null)
            field -> destinationValue
          } else {
            "_missingField" -> null
          }
        })
          .filter(v => v._2 != null)
          .toMap ++ unspecifiedAttributes + destinationSubscriberMap + destinationSubscriberRelationMap

        val currentAttributes = identityMigrationFieldsBC.value.map(v => {
          val field = v.get("field").get
          val currentField = v.get("currentField").get
          if (eventKeys.contains(currentField)) {
            val currentValue = event.curatedRecordJson.getOrElse(currentField, null)
            field -> currentValue
          } else {
            if (eventKeys.contains(field)) {
              val destinationValue = event.curatedRecordJson.getOrElse(field, null)
              field -> destinationValue
            } else {
              "_missingField" -> null
            }
          }
        })
          .filter(v => v._2 != null)
          .toMap ++ unspecifiedAttributes + subscriberMap + subscriberRelationMap

        val destinationIdentity = Identity.createIdentityWithIdentifyingAttributes(destinationAttributes, identifyingAttributesBC.value)
        val currentIdentity = Identity.createIdentityWithIdentifyingAttributes(currentAttributes, identifyingAttributesBC.value)

        val destinationIdentityId = destinationIdentity.identityId
        val currentIdentityId = currentIdentity.identityId
        List(destinationIdentityId, currentIdentityId)
      })
    } else spark.sparkContext.emptyRDD[List[Long]]
  }

  //region end - identity migration

  /**
   * Log pinning process started
   */
  private[this] def logPinningProcessStarted() = {

    puLogger.info(ProcessUpdateEvent(jobId = "", createdBy = "PINNER", location = "", datasetId = "",
      target = "pinned", processType = "PROCESS_START",
      stage = "PINNING", status = "IN_PROGRESS", comments = "Pinning started",
      additionalInformation = "", eventTimestamp = "", applicationId = appContext.sc.applicationId,
      jobStartTimestamp = timestamp,
      edgeNode = edgenodeHostname, pid = pid.toString,
      messageId = Instant.now().toEpochMilli.toString).toString)
  }

  /**
   * Log pinning process completed successfully
   */
  private[this] def logPinningProcessCompletedSuccessfully() = {

    puLogger.info(ProcessUpdateEvent(jobId = "", createdBy = "PINNER", location = "", datasetId = "",
      target = "pinned", processType = "PROCESS_END",
      stage = "PINNING", status = "SUCCESS", comments = "Pinning completed successfully",
      additionalInformation = "", eventTimestamp = "", applicationId = appContext.sc.applicationId,
      jobStartTimestamp = timestamp,
      edgeNode = edgenodeHostname, pid = pid.toString,
      messageId = Instant.now().toEpochMilli.toString).toString)
  }

  /**
   * Log pinning process failed
   */
  private[this] def logPinningProcessFailed(errorMessage: String) = {

    puLogger.info(ProcessUpdateEvent(jobId = "", createdBy = "PINNER", location = "", datasetId = "",
      target = "pinned", processType = "PROCESS_END",
      stage = "PINNING", status = "FAILED", comments = "Pinning failed",
      additionalInformation = errorMessage, eventTimestamp = "", applicationId = appContext.sc.applicationId,
      jobStartTimestamp = timestamp,
      edgeNode = edgenodeHostname, pid = pid.toString,
      messageId = Instant.now().toEpochMilli.toString).toString)
  }


  //region start - static pinning
  /**
   * Read identity-pin map file
   *
   * @param filename identity-pin map filename
   * @return identity-pin map RDD
   */
  private[oxygen] def readIdentityPinMapFile(filename: String): RDD[PinnedTaggedIdentity] = {
    try {
      //oxygenIO.read.dataframe(filename, oxygenConf.oxygen.STATIC_PINNING_FORMAT).as[PinnedTaggedIdentity].rdd
      oxygenIO.read.pinnedTaggedIdentity(filename,oxygenConf.oxygen.STATIC_PINNING_FORMAT)
    } catch {
      case exception: Exception => {
        logger.warn(s"Unable to read Identity Pin Map. Proceeding assuming there are NO previous pins generated for inferring static pins. - Exception message : ${exception.getMessage}")
        logger.debug(exception.getMessage, exception)
        appContext.sc.emptyRDD[PinnedTaggedIdentity]
      }
    }
  }
  override def readIdentifyingAttributes(filename: String): Array[String] = super.readIdentifyingAttributes(filename)

  override def readIdentityMigrationConfAsString(filename: String): String = super.readIdentityMigrationConfAsString(filename)

  /**
   * Read from Hbase for identity-pin map
   *
   * @param identityPinMapDAO identity-pin map DAO for Hbase
   * @return identity-pin map RDD
   */
  private def readFromHbaseForIdentityPinMap(identityPinMapDAO: IdentityPinMapDAO): RDD[PinnedTaggedIdentity] = {
    val identityPinMapFromHB = identityPinMapDAO.readPinnedTaggedIdentityMap()
    val rddIdentityPinMap = if(identityPinMapFromHB.length != 0)  appContext.sc.parallelize(identityPinMapFromHB.toList)
    else appContext.sparkSession.emptyDataset[PinnedTaggedIdentity].rdd

    rddIdentityPinMap
  }

  /**
   * Emit stock metric
   *
   * @param name
   * @param key
   * @param value Long
   * @param extraFields
   * @return
   */
  def emitStockMetric(name: String, key: String, value: Long, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.PINNING, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  /**
   * Emit stock metric
   *
   * @param name
   * @param key
   * @param value Double
   * @param extraFields
   * @return
   */
  def emitStockMetric(name: String, key: String, value: Double, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.PINNING, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  /**
   * Emit stock metric
   *
   * @param name
   * @param key
   * @param value String
   * @param extraFields
   * @return
   */
  def emitStockMetric(name: String, key: String, value: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitInfo(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.PINNING, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  /**
   * Get VLE pins from vleManagerBC
   *
   * @return Array of VLE pins
   */
  def getVlePins(): Array[Long] = {
    if (vleManagerBC.value.isDefined) vleManagerBC.value.get.vleValues.map(_._2.getPin.toLong).toArray.distinct else Array.empty[Long]
  }

  /**
   * Read files from various format ("text", "parquet", and "avro") into spark dataframe.
   *
   * @param filename filename
   * @param readFormat file format
   * @return file data in spark dataframe
   */
  private def readFiles(filename: String, readFormat: String = readFormat): DataFrame = {
    logger.info("Reading " + filename + " in format " + readFormat)
    readFormat match {
      case Constants.FILE_AVRO_FORMAT => spark.read.format("avro").option("encoding", "UTF-8").load(filename)
      case Constants.FILE_PARQUET_FORMAT => spark.read.option("encoding", "UTF-8").parquet(filename)
      case Constants.FILE_TEXT_FORMAT => spark.read.option("encoding", "UTF-8").json(filename)
      case _ => throw new UnsupportedOperationException
    }
  }

  /**
   * Write file in various format ("text", "parquet", and "avro") from spark dataframe.
   *
   * @param df spark dataframe as source
   * @param fileToWrite output file path
   * @param writeFormat output file format ("text", "parquet", and "avro"); default is "avro"
   * @param writeMode write mode ("overwrite" or "append"); default is "overwrite"
   * @return file data in spark dataframe
   */
  private def writeFile(df: DataFrame, fileToWrite: String, writeFormat: String = "avro", writeMode: String = "overwrite") = {
    logger.info("Writing " + fileToWrite + " in format " + writeFormat)
    writeFormat match {
      case Constants.FILE_AVRO_FORMAT => df.write.mode(writeMode).format("avro").option("encoding", "UTF-8").save(fileToWrite)
      case Constants.FILE_PARQUET_FORMAT => df.write.mode(writeMode).option("encoding", "UTF-8").parquet(fileToWrite)
      case Constants.FILE_TEXT_FORMAT => df.write.mode(writeMode).option("encoding", "UTF-8").json(fileToWrite)
      case _ => throw new UnsupportedOperationException
    }
    logger.info(s"Write completed for $fileToWrite")
  }

  /**
   * Process pin-index map.
   * Each indexName creates a pin-index table in the "../pinning/pinned/pin-index/" directory.
   *
   * @param pinIndexFile pinIndex configuration json file
   * @param identityPinMapFilename identityPinMap path of this generation
   * @param identityPinMapFileFormat identityPinMap format
   */
  def processPinIndex(pinIndexFile: String, identityPinMapFilename: String, identityPinMapFileFormat: String): Unit = {
    val indexStrings = readPinIndexFileAsString(pinIndexFile)
    val pinIndexUnionOutputPath = pinnedDir + "/pin-index"
    val pinIndexOutputRootTmpPath = pinnedDir + "/pin-index-tmp/"
    if (fileUtils.exists(pinIndexOutputRootTmpPath)) fileUtils.deleteFileOrFolder(pinIndexOutputRootTmpPath)
    val indexArray = indexStrings.split("\n").map(v => {
      ScalaJacksonUtil.fromJson[PinIndex](v).getOrElse(PinIndex("", List[String]()))
    })
      .filter(v => v.name != "" || v.keys.size > 0)
    indexArray.foreach(v => {
      val indexName = v.name
      val indexAttributes = v.keys
      val identityPinMapDf = readFiles(identityPinMapFilename, identityPinMapFileFormat)
      val indexIdPinDf = indexAttributes.foldLeft(identityPinMapDf)((df, c) => df.withColumn(c, expr(s"taggedIdentity.identity.attributes.$c")))
        .select((Array("pin") ++ indexAttributes).map(col): _*)
        .distinct
        .rdd.map(row => {
        val pin = row(0).asInstanceOf[Long]
        val attributes = scala.collection.mutable.Map[String, String]()
        for (i <- 1 to row.size - 1) {
          val value = row(i).asInstanceOf[String]
          val field = indexAttributes(i - 1)
          attributes += (field -> value)
        }
        val indexId = Identity(attributes.toMap + ("indexName" -> v.name)).identityId
        (indexId, pin)
        })
        .toDF("indexId", "pin")
        .groupBy("indexId").agg(collect_set("pin").as("pin"))

      val pinIndexOutputPath = pinIndexOutputRootTmpPath + indexName
      writeFile(indexIdPinDf, pinIndexOutputPath, identityPinMapFileFormat)
      logger.info(s"pin-index: $indexName written to $pinIndexOutputPath.")
    })

    val pinIndexUnionDf = indexArray.map(v => {
      val pinIndexOutputPath = pinIndexOutputRootTmpPath + v.name
      readFiles(pinIndexOutputPath, identityPinMapFileFormat)
    }).reduce(_.union(_))
    writeFile(pinIndexUnionDf, pinIndexUnionOutputPath, identityPinMapFileFormat)
    logger.info(s"pin-index written to $pinIndexUnionOutputPath.")

    val pinIndexValidationDf = readFiles(pinIndexUnionOutputPath, identityPinMapFileFormat)
    val conflictCountDf = pinIndexValidationDf.select("indexId").groupBy("indexId").count.filter("count > 1").cache()
    val conflictCount = conflictCountDf.count
    if (conflictCount > 0) {
      val conflictDf = conflictCountDf.select("indexId").join(pinIndexValidationDf, Seq("indexId"), "left")
      val conflictOutputPath = pinIndexUnionOutputPath + "-conflict"
      writeFile(conflictDf, conflictOutputPath, "text")
      val showGet = new ByteArrayOutputStream
      Console.withOut(showGet) { conflictDf.show(conflictCount.toInt.min(1000), false) }
      val showString = new String(showGet.toByteArray)
      throw new Exception("Conflicts in pin-index!\nConflicts written to: " + conflictOutputPath + "\n" + showString)
    } else {
      if (fileUtils.exists(pinIndexOutputRootTmpPath)) fileUtils.deleteFileOrFolder(pinIndexOutputRootTmpPath)
      logger.info(s"Removed pin-index-tmp: $pinIndexOutputRootTmpPath.")
    }




}

  private[pinning] def saveBlockDoesMatchProfileData(doesMatchedIdentities: RDD[String]) = {

    val filename = pinnedDir + File.separator + timestamp + "-doesMatchProfileData"
    doesMatchedIdentities.saveAsTextFile(filename)
  }

}

case class TaggedIdentityWithEventId(taggedIdentity: TaggedIdentity, eventIds: List[String], pin: Long) extends Serializable

case class MergedPin(pins: List[Long]) extends Serializable

case class SplitPin(oldPin: Long, newPins: List[Long]) extends Serializable

case class StampedEventId(pin: Long, eventIds: List[String])

case class PinnedEvent(pin: Long, taggedCuratedEvent: TaggedCuratedEvent) extends Serializable {

  def write: String = JsonUtils.toJson(this)
  override def toString: String = write

}