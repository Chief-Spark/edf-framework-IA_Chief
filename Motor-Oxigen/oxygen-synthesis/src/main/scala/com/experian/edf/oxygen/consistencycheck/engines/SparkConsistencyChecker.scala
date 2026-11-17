package com.experian.edf.oxygen.consistencycheck.engines

import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingLogging
import com.experian.edf.oxygen.consistencycheck.standalone.Synthesizer
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck._
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationProcessor
import com.experian.edf.oxygen.consistencycheck.standalone.custom.CustomRuleChecker
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ConsistencyCheckOps
import com.experian.edf.oxygen.configuration.configs.ConsistencyCheckCategoryElement
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.consistencycheck.domain._
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedConsistencyInfo}
import com.experian.edf.oxygen.domain.processors.MigrationProcessorContext
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.processmngt.{HttpSynchronousClient, ProcessManagementClient}
import com.google.gson.JsonParser
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel

import java.io.File
import java.time.{ZoneId, ZonedDateTime}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ListBuffer

/**
 * Consistency check and Synthesis run in the spark mode using Synthesizer from Oxygen-framework-synthesis
 *
 * @param appContext
 */
class SparkConsistencyChecker(appContext: SparkOxygenAppContext) extends ConsistencyChecker(appContext)
  //with ReasonabilityProcess[TaggedValidatedEvent]
{


 // private val currentTimestamp = Instant.now.atZone(ZoneId.of("Etc/UTC")).toInstant.toEpochMilli  //CHECK with stage timestamp
  val sparkSession = appContext.sparkSession // Spark session

  val synthesisIOUtils = new SynthesisIOUtils(fileUtils, appContext)

  val listFileTypes: List[String] = oxygenConf.oxygen.stage.consistency.get.eventTypeList //oxygenConf.oxygen.EVENT_TYPE_LIST
  val dateTypePick: String = oxygenConf.oxygen.stage.consistency.get.dateType.get //oxygenConf.oxygen.DATE_TYPE.get
  val checkFileTagEnabled: Boolean = oxygenConf.oxygen.stage.consistency.get.checkFileTag //oxygenConf.oxygen.CHECK_FILE_TAG
  val referenceEventLimit: Option[Int] = oxygenConf.oxygen.stage.consistency.get.referenceEventsWindowInMonths //oxygenConf.oxygen.REFERENCE_EVENT_WINDOW
  val consistencyCheckCategory: List[ConsistencyCheckCategoryElement] = oxygenConf.oxygen.stage.consistency.get.checkCategory
  val shouldFilterDeletedEvent: Boolean = oxygenConf.oxygen.stage.consistency.get.shouldFilterDeletedEvents //oxygenConf.oxygen.SHOULD_FILTER_DELETED_EVENT
  val shouldReportDeletedEvent: Boolean = oxygenConf.oxygen.stage.consistency.get.shouldReportAllDeletedEvents //oxygenConf.oxygen.SHOULD_REPORT_ALL_DELETED_EVENT
  val subscriberMigrationLoggerEnabled = oxygenConf.oxygen.stage.consistency.get.migrationLoggingEnabled
  val currDateTime: Long = ZonedDateTime.now(ZoneId.of("Etc/UTC")).toEpochSecond * 1000

  val KafkaMsg = new KafkaMsg(oxygenConf)
  //private val reasonability = appContext.config.oxygen.SYNTHESIS_REASONABILITY
  //private val metrics = new ReasonabilityProcess()

  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")

  private val metricsMetadata = Map(MetricsConstants.Keys.GENERATION -> oxygenConf.oxygen.generation.candidate.getOrElse("default"))

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.SYNTHESIS,
    source = MetricsConstants.Sources.SYNTHESIZER,
    orchestrationSource = oxygenConf.oxygen.stage.consistency.get.orchestration.get.source,
    orchestrationTarget = oxygenConf.oxygen.stage.consistency.get.orchestration.get.target,
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId)
  )

  /**
   * Infer Delete V2: Feature that generate flag on Validation stage with fileTag with FileMeta and send this fileTag to OpsAnalytics and on
   * Consistency stage, we will compare for each record with latest fileTag and mark with a flag, "voidIndicator".
   * And if data have this flag, it can be filtered on stateCalc stage. Oxygen+Release+-+0.98.200131.95
   * Usecase: Truncate and full load of a file - synthesis is always from the latest file.If the data is not available in the latest file,
   * old data shouldnt be used.Frequency of these files are unique
   * Below 4 methods are related to this feature
   * getEachFileTypeLatest
   * getLatestTimeForEachType
   * getHistroicArgs
   * getTimeForEachFileType
   */
  private def getEachFileTypeLatest(pmConn: HttpSynchronousClient, status: String, stage: String): List[String] = {
    val response = listFileTypes.map(each => pmConn.getLatestTime(each.toString, dateTypePick, status, stage))
    response
  }

  private def getLatestTimeForEachType(eachResponse: List[String], dateType: String): ConcurrentHashMap[String, Long] = {
    val mapEachFileType: ConcurrentHashMap[String, Long] = new ConcurrentHashMap
    eachResponse.map(eachContent => {
      val jsonStringAsObject = new JsonParser().parse(eachContent).getAsJsonObject.get("ingesteditems")
      val size = jsonStringAsObject.getAsJsonArray.size()
      if (size != 0) {
        val eachFileTag = jsonStringAsObject.getAsJsonArray.get(0).getAsJsonObject.get("datasetMetadata").getAsJsonObject.get("fileTag").getAsJsonPrimitive.getAsString
        val eachDate = jsonStringAsObject.getAsJsonArray.get(0).getAsJsonObject.get("datasetMetadata").getAsJsonObject.get(dateType).getAsJsonPrimitive.getAsLong
        mapEachFileType.put(eachFileTag, eachDate)
      }
    }
    )
    mapEachFileType
  }

  private[consistencycheck] def getHistroicArgs(historicAttrArgs: String): (String, String) = {
    val (status: String, stage: String) = if (historicAttrArgs.isEmpty) {
      // Getting default, 20 records and Success
      ("status:SUCCESS", "stage=Pinning")
    } else {
      val jsonObject = new JsonParser().parse(historicAttrArgs).getAsJsonObject
      val status = jsonObject.get("status").getAsString
      val stage = jsonObject.get("stage").getAsString
      (status, stage)
    }
    (status, stage)
  }

  private def getTimeForEachFileType: ConcurrentHashMap[String, Long] = {
    if (checkFileTagEnabled) {
      val pmConn = ProcessManagementClient.getOrCreate(oxygenConf)
      val historicAttrArgs = oxygenConf.oxygen.stage.consistency.get.historicAttributeArgs.get // oxygenConf.oxygen.HISTORIC_ATTR_ARGS.get
      val (histStatus, stage) = getHistroicArgs(historicAttrArgs)
      val eachResponse = getEachFileTypeLatest(pmConn, histStatus, stage)
      getLatestTimeForEachType(eachResponse, dateTypePick)
    } else {
      val emptyMap: ConcurrentHashMap[String, Long] = new ConcurrentHashMap
      emptyMap
    }
  }

  private def retrieveHCDataWithBalancedSSRs(numPartitionsForBalancedSSRs: Int): RDD[HeterogeneousCollection] = {
    import sparkSession.implicits._

    val hcTmpDf = synthesisIOUtils.retrieveHCDataDf
    val hcDfCols = hcTmpDf.columns
    val pinSSRCountDf = hcTmpDf
      .selectExpr("pin", "size(array_distinct(transform(events, x -> concat_ws(':', x.subscriber, x.subscriberRelation)))) as ssrCount")
      .persist(StorageLevel.DISK_ONLY)

    val maxSSRCount = pinSSRCountDf.selectExpr("max(ssrCount) as maxSSRCount").first().getInt(0).toLong
    val totalSSRCount = pinSSRCountDf.selectExpr("sum(ssrCount) as totalSSRCount").first().getLong(0)
    val maxSSRCountPerPartition = maxSSRCount.max((totalSSRCount / numPartitionsForBalancedSSRs).floor.toLong + 1L)
    logger.error(s"Max SSR count for a pin: ${maxSSRCount.toString}")
    logger.error(s"Total SSR count: ${totalSSRCount.toString}")
    logger.error(s"Max SSR count per partition: ${maxSSRCountPerPartition.toString}")

    pinSSRCountDf
      .withColumn("countCumSum", sum(col("ssrCount")).over(Window.orderBy(col("ssrCount")).rowsBetween(Window.unboundedPreceding, Window.currentRow)))
      .withColumn("partitionId", (col("countCumSum") / lit(maxSSRCountPerPartition)).cast("integer"))
      .select("pin", "partitionId")
      .join(hcTmpDf, Seq("pin"), "right")
      .repartition(col("partitionId"))
      .select(hcDfCols.head, hcDfCols.tail: _*)
      .as[HeterogeneousWritableCollection]
      .rdd.map(HeterogeneousWritableCollection.toHeterogeneousCollection)
      .persist(StorageLevel.DISK_ONLY)
  }

  private def retrieveHCDataDelta(): RDD[HeterogeneousCollection] = {
    import sparkSession.implicits._

    val hcDf = synthesisIOUtils.retrieveHCDataDfDelta
    hcDf.as[HeterogeneousWritableCollection]
      .rdd.map(HeterogeneousWritableCollection.toHeterogeneousCollection)
  }

  private def retrieveHCDataNew(): RDD[HeterogeneousCollection] = {
    import sparkSession.implicits._

    val hcDf = synthesisIOUtils.retrieveHCDataDfNew
    hcDf.as[HeterogeneousWritableCollection]
      .rdd.map(HeterogeneousWritableCollection.toHeterogeneousCollection)
  }

  private def retrieveHCDataDeltaAccountLevel(): RDD[HeterogeneousCollection] = {
    import sparkSession.implicits._

    val hcDf = synthesisIOUtils.retrieveHCDataDfDeltaAccountLevel
    hcDf.as[HeterogeneousWritableCollection]
      .rdd.map(HeterogeneousWritableCollection.toHeterogeneousCollection)
  }

  override def run: Unit = {

    startService()

    val newDatasetsToProcess = orchestrator.retrieveAllDatasetsToProcess()
    if (appContext.config.oxygen.DEV_MODE || (newDatasetsToProcess.isDefined && newDatasetsToProcess.get.nonEmpty)) {
      logger.info("Consistency Check Started")
      val sc = appContext.sc

      // get file locations from  the application conf
      val consistencyCheckDictionaryFile = oxygenConf.oxygen.stage.consistency.get.paths.dictionaryFile
      val domainDictionaryFile = oxygenConf.oxygen.stage.consistency.get.paths.domainDictionaryFile
      val customRulesFile = oxygenConf.oxygen.stage.consistency.get.paths.customRulesFile
      val relationshipMigrationProcessorClassName = oxygenConf.oxygen.stage.consistency.get.relationshipMigrationProcessorClassName
      val numPartitionsForBalancedSSRs = oxygenConf.configMap.get("oxygen.stage.consistencyCheck.numPartitionsForBalancedSSRs").getOrElse("0").toInt
      val numPartitionsForOutput = oxygenConf.configMap.get("oxygen.stage.consistencyCheck.numPartitionsForOutput").getOrElse("0").toInt
      val deltaModeAccountLevel = oxygenConf.configMap.get("oxygen.stage.consistencyCheck.deltaModeAccountLevel").getOrElse("false").toBoolean
      val deltaMode = if (deltaModeAccountLevel) false else oxygenConf.configMap.get("oxygen.stage.consistencyCheck.deltaMode").getOrElse("false").toBoolean
      val fieldHandlerHashFunction = oxygenConf.oxygen.stage.consistency.get.fieldHandlerHashFunction.get
      val defaultOutputStructure = oxygenConf.configMap.get("oxygen.features.enable.defaultOutputStructure").getOrElse("false").toBoolean

      logger.info("hash function for field Handler " + fieldHandlerHashFunction)
      logger.info("consistencyCheckDictionaryFile " + consistencyCheckDictionaryFile)
      logger.info("domainDictionaryFile " + domainDictionaryFile)
      logger.info("customRulesFile " + customRulesFile)
      logger.info("relationshipMigrationProcessorClassName " + relationshipMigrationProcessorClassName)
      logger.info("subscriberMigrationLoggerEnabled " + subscriberMigrationLoggerEnabled)
      if (numPartitionsForBalancedSSRs!=0) logger.info("numPartitionsForBalancedSSRs " + numPartitionsForBalancedSSRs.toString) else logger.info("numPartitionsForBalancedSSRs " + "N/A")
      if (numPartitionsForOutput!=0) logger.info("numPartitionsForOutput " + numPartitionsForOutput.toString) else logger.info("numPartitionsForOutput " + "N/A")
      logger.error("deltaMode " + deltaMode.toString)
      logger.error("deltaModeAccountLevel " + deltaModeAccountLevel.toString)

      logger.info("hash function for field Handler " + fieldHandlerHashFunction)

      logger.info("subscriberMigrationLoggerEnabled " + subscriberMigrationLoggerEnabled)
      if (numPartitionsForBalancedSSRs!=0) logger.info("numPartitionsForBalancedSSRs " + numPartitionsForBalancedSSRs.toString) else logger.info("numPartitionsForBalancedSSRs " + "N/A")
      if (numPartitionsForOutput!=0) logger.info("numPartitionsForOutput " + numPartitionsForOutput.toString) else logger.info("numPartitionsForOutput " + "N/A")
      logger.error(s"defaultOutputStructure : $defaultOutputStructure")

      // retrieve Heterogeneous Collection
      val hc: RDD[HeterogeneousCollection] = if (deltaModeAccountLevel) {
        val hcNewDir = oxygenConf.oxygen.PINNED_DIRECTORY.stripSuffix(File.separator).split(File.separator).dropRight(1).mkString(File.separator) + File.separator + "hc-delta" + File.separator + "detected" + File.separator + "new"
        if (fileUtils.exists(hcNewDir)) retrieveHCDataNew else sparkSession.sparkContext.emptyRDD[HeterogeneousCollection]
      } else if (deltaMode) {
        retrieveHCDataDelta
      } else {
        if (numPartitionsForBalancedSSRs == 0) {
          synthesisIOUtils.retrieveHCData
        } else {
          retrieveHCDataWithBalancedSSRs(numPartitionsForBalancedSSRs)
        }
      }

      // Initialize lookup service. Broadcasted oxygen lookup service has to be passed in the function calls to avail the service else it is set to null
      val lookupService = OxygenLookupService.getServiceB.value

      // Read all the required files for checks and domain dictionary
      val domainDictionary = ConsistencyCheckOps.readDomainDictionary(domainDictionaryFile.head)
      val consistencyCheckDictionaryWithReferenceCache = ConsistencyCheckOps.readExtensionChecksAndReferenceCache(consistencyCheckDictionaryFile(0), lookupService, domainDictionary)
      val consistencyCheckDictionary = consistencyCheckDictionaryWithReferenceCache._2
      val referenceCache = consistencyCheckDictionaryWithReferenceCache._1
      val customRules = CustomRuleChecker.readCustomRules(customRulesFile.head)
      val currDateTime = ZonedDateTime.now(ZoneId.of("Etc/UTC")).toEpochSecond * 1000
      val migrationProcessorContext: MigrationProcessorContext = MigrationProcessorContext(lookupService)

      // If not migrationProcessor specified, use the default one.
      val relationshipMigrationProcessor = if (relationshipMigrationProcessorClassName == null || relationshipMigrationProcessorClassName == "")
        null
      else
        Class.forName(relationshipMigrationProcessorClassName.trim).getConstructor(migrationProcessorContext.getClass).newInstance(migrationProcessorContext).asInstanceOf[RelationshipMigrationProcessor]

      // Get time for each fileType
      val fileTypeTimeMap = getTimeForEachFileType
     // val stateCheckAttributes = StateCheckAttributes(oxygenConf.oxygen.STATE_CHECK_ENABLED, oxygenConf.oxygen.STATE_NAMES.getOrElse(""), oxygenConf.oxygen.STATE_ID.getOrElse(""))
     val stateCheckAttributes = StateCheckAttributes(oxygenConf.oxygen.stage.consistency.get.stateCheckEnabled, oxygenConf.oxygen.stage.consistency.get.stateNames.getOrElse(""), oxygenConf.oxygen.stage.consistency.get.stateId.getOrElse(""))
      val contextualOverrideAttributes = ContextualOverrideAttributes(shouldFilterDeletedEvent, shouldReportDeletedEvent)
      val checkCategoryElementList = consistencyCheckCategory.map(x => CheckCategoryElement(x.categoryName, x.categoryThreshold))
      val synthesisOptions = SynthesisOptions(referenceEventLimit, fileTypeTimeMap, checkFileTagEnabled, dateTypePick, checkCategoryElementList, stateCheckAttributes, contextualOverrideAttributes, subscriberMigrationLoggerEnabled)

      // Initialize Synthesizer and broadcast
      val synthesizer = sc.broadcast(new Synthesizer(domainDictionary,
        consistencyCheckDictionary,
        referenceCache,
        lookupService,
        currDateTime,
        relationshipMigrationProcessor, customRules, synthesisOptions , fieldHandlerHashFunction))

      // for each pin perform checks and Synthesize
      val consistencyRDD: RDD[(Long, (List[TaggedConsistencyInfo], List[SyntheticEvent]))] = if (emitStockMetricsEnabled) {
        hc.mapPartitionsWithIndex {
          (partitionId, eachPartitionHC) => {
            var consistencyInfoCount = 0
            var syntheticEventCount = 0
            var syntheticPinCount = 0
            val listIter = new ListBuffer[(Long, (List[TaggedConsistencyInfo], List[SyntheticEvent]))]
            val listLogging = new ListBuffer[RelationshipMigrationTrackingLogging]

            while (eachPartitionHC.hasNext) {
              val eachHC = eachPartitionHC.next
              try {
                logger.debug(s"-| Performing consistency checks for pin: {} ", eachHC.pin.toString)
                // Perform checks and defaultSynthesizer
                val (synthesizedEventsWrapper, migrationLogging) = synthesizer.value.synthesize(eachHC)
                syntheticEventCount += synthesizedEventsWrapper.syntheticEventCount
                consistencyInfoCount += synthesizedEventsWrapper.consistencyInfoCount
                syntheticPinCount += 1
                listIter ++= List((synthesizedEventsWrapper.pin, (synthesizedEventsWrapper.consistencyInfo, synthesizedEventsWrapper.syntheticEvents)))
                synthesisIOUtils.sendSubscriberMigrationInfo(migrationLogging)
              }
              catch {
                case e: Exception => {
                  logger.debug(s"Consistency Check failed due to : ${e.getCause} + for pin ${eachHC.pin}")
                  logger.debug(s"Failed with stack trace for pin ${eachHC.pin}", e)
                  listIter ++= List((eachHC.pin, (List.empty[TaggedConsistencyInfo], List.empty[SyntheticEvent])))
                }
              }
            }
            //          logger.debug(s"-| Metric info: partitionId: {}, syntheticPinCount: {}, syntheticEventCount: {}, consistencyInfoCount: {} ",
            //            partitionId.toString, syntheticPinCount.toString, syntheticEventCount.toString, consistencyInfoCount.toString)

            emitStockMetric("syntheticPinCountPartition", "syntheticPinCountPartition", syntheticPinCount, metricsMetadata + ("partitionId" -> partitionId))
            emitStockMetric("syntheticEventCountPartition", "syntheticEventCountPartition", syntheticEventCount, metricsMetadata + ("partitionId" -> partitionId))
            emitStockMetric("allInconsistenciesCountPartition", "allInconsistenciesCountPartition", consistencyInfoCount, metricsMetadata + ("partitionId" -> partitionId))

            listIter.iterator
          }
        }
      } else {
        hc.map {
          eachHC => {
              try {
                logger.debug(s"-| Performing consistency checks for pin: {} ", eachHC.pin.toString)
                // Perform checks and defaultSynthesizer
                val (synthesizedEventsWrapper, migrationLogging) = synthesizer.value.synthesize(eachHC)
                synthesisIOUtils.sendSubscriberMigrationInfo(migrationLogging)
                (synthesizedEventsWrapper.pin, (synthesizedEventsWrapper.consistencyInfo, synthesizedEventsWrapper.syntheticEvents))
              }
              catch {
                case e: Exception => {
                  logger.debug(s"Consistency Check failed due to : ${e.getCause} + for pin ${eachHC.pin}")
                  logger.debug(s"Failed with stack trace for pin ${eachHC.pin}", e)
                  (eachHC.pin, (List.empty[TaggedConsistencyInfo], List.empty[SyntheticEvent]))
                }
              }
          }
        }
      }
      .persist(StorageLevel.DISK_ONLY)

      val allInconsistenciesRDD = consistencyRDD.flatMap(eachPinLevelInfo => {
        eachPinLevelInfo._2._1
      }).persist(StorageLevel.DISK_ONLY)

      val syntheticEventsWithPinRDD = consistencyRDD.map(eachPinLevelInfo => {
        (eachPinLevelInfo._1, eachPinLevelInfo._2._2)
      })
      // save synthesis output: consistency info and the synthetic events
      if (deltaModeAccountLevel) {
        synthesisIOUtils.saveSynthesisOutputNew(allInconsistenciesRDD, syntheticEventsWithPinRDD)
      } else if (deltaMode) {
        synthesisIOUtils.saveSynthesisOutputDelta(allInconsistenciesRDD, syntheticEventsWithPinRDD)
      } else  {
        if (numPartitionsForOutput == 0) synthesisIOUtils.saveSynthesisOutput(allInconsistenciesRDD, syntheticEventsWithPinRDD)
        else synthesisIOUtils.saveSynthesisOutput(allInconsistenciesRDD, syntheticEventsWithPinRDD.repartition(numPartitionsForOutput))
      }

      consistencyRDD.unpersist()
      allInconsistenciesRDD.unpersist()

      logger.info(s"Consistency Check Ended")

      // Save unchanged reference pins
      if (deltaModeAccountLevel || deltaMode) {
        val checkedUnchangedDir = SynthesisIOUtils.makeConsistencyPathUnchanged(oxygenConf)
        val synthesizedUnchangedDir = SynthesisIOUtils.makeSynthesizedPathUnchanged(oxygenConf)
        if (fileUtils.exists(checkedUnchangedDir)) fileUtils.deleteFileOrFolder(checkedUnchangedDir)
        if (fileUtils.exists(synthesizedUnchangedDir)) fileUtils.deleteFileOrFolder(synthesizedUnchangedDir)

        val referenceRootDir = oxygenConf.oxygen.stage.consistency.get.paths.consistencyReferenceDir
        val checkedReferenceDir = referenceRootDir + File.separator + "checked" + File.separator + "*" + File.separator + "*"
        val synthesizedReferenceDir = referenceRootDir + File.separator + "synthesized" + File.separator + "*" + File.separator + "*"

        val candidateGenerationHome = oxygenConf.configMap.get("oxygen.application.paths.candidateGenerationHome").getOrElse("")
        val hcDeltaDir = candidateGenerationHome + File.separator + "pinning" + File.separator + "hc-delta" + File.separator + "detected"
        val deletedPath = hcDeltaDir + File.separator + "deleted"
        val changedPath = hcDeltaDir + File.separator + "changed"

        if (referenceRootDir.stripSuffix(File.separator + "consistency") == candidateGenerationHome) throw new Exception("Candidate and Reference generations are the same!")
        synthesisIOUtils.getAndSaveUnchanged(checkedUnchangedDir, synthesizedUnchangedDir, checkedReferenceDir, synthesizedReferenceDir, deletedPath, changedPath)
      }

      if (deltaModeAccountLevel) {
        val consistencyFilename = SynthesisIOUtils.makeConsistencyPathDeltaAccountLevel(oxygenConf)
        if (fileUtils.exists(consistencyFilename)) fileUtils.deleteFileOrFolder(consistencyFilename)

        val hcDeltaAccountLevelDir = oxygenConf.oxygen.PINNED_DIRECTORY.stripSuffix(File.separator).split(File.separator).dropRight(1).mkString(File.separator) + File.separator + "hc-delta" + File.separator + "detected-account-level"
        if (!fileUtils.exists(hcDeltaAccountLevelDir)) {
          logger.error("No account level delta: " + hcDeltaAccountLevelDir)
          return
        }

        val hcAccountLevel: RDD[HeterogeneousCollection] = retrieveHCDataDeltaAccountLevel

        val consistencyAccountLevelRDD: RDD[(Long, (List[TaggedConsistencyInfo], List[SyntheticEvent]))] = hcAccountLevel
          .map {
            eachHC => {
              try {
                logger.debug(s"-| Performing account level consistency checks for pin: {} ", eachHC.pin.toString)
                // Perform checks and defaultSynthesizer
                val (synthesizedEventsWrapper, migrationLogging) = synthesizer.value.synthesize(eachHC)
                synthesisIOUtils.sendSubscriberMigrationInfo(migrationLogging)
                (synthesizedEventsWrapper.pin, (synthesizedEventsWrapper.consistencyInfo, synthesizedEventsWrapper.syntheticEvents))
              }
              catch {
                case e: Exception => {
                  logger.debug(s"Account level consistency Check failed due to : ${e.getCause} + for pin ${eachHC.pin}")
                  logger.debug(s"Failed with stack trace for pin ${eachHC.pin}", e)
                  (eachHC.pin, (List.empty[TaggedConsistencyInfo], List.empty[SyntheticEvent]))
                }
              }
            }
          }
          .persist(StorageLevel.DISK_ONLY)

        val allInconsistenciesAccountLevelRDD = consistencyAccountLevelRDD.flatMap(eachPinLevelInfo => {
          eachPinLevelInfo._2._1
        }).persist(StorageLevel.DISK_ONLY)

        val syntheticEventsWithPinAccountLevelRDD = consistencyAccountLevelRDD.map(eachPinLevelInfo => {
          (eachPinLevelInfo._1, eachPinLevelInfo._2._2)
        }).persist(StorageLevel.DISK_ONLY)

        // save synthesis output: consistency info and the synthetic events

        synthesisIOUtils.saveCheckedOutputDeltaAccountLevel(allInconsistenciesAccountLevelRDD)

        allInconsistenciesAccountLevelRDD.unpersist()

        logger.info(s"Account level consistency Check Ended")

        // Save unchanged reference on account level
        val checkedUnchangedDir = SynthesisIOUtils.makeConsistencyPathUnchangedAccountLevel(oxygenConf)
        if (fileUtils.exists(checkedUnchangedDir)) fileUtils.deleteFileOrFolder(checkedUnchangedDir)

        val referenceRootDir = oxygenConf.oxygen.stage.consistency.get.paths.consistencyReferenceDir
        val checkedReferenceDir = referenceRootDir + File.separator + "checked" + File.separator + "*" + File.separator + "*"
        val synthesizedReferenceDir = referenceRootDir + File.separator + "synthesized" + File.separator + "*" + File.separator + "*"

        val candidateGenerationHome = oxygenConf.configMap.get("oxygen.application.paths.candidateGenerationHome").getOrElse("")
        val hcDeltaDir = candidateGenerationHome + File.separator + "pinning" + File.separator + "hc-delta" + File.separator + "detected-account-level"
        val unchangedPath = hcDeltaDir + File.separator + "unchanged"

        if (referenceRootDir.stripSuffix(File.separator + "consistency") == candidateGenerationHome) throw new Exception("Candidate and Reference generations are the same!")
        val synthesizedAccountLevelUnchangedDf = synthesisIOUtils.getAndSaveUnchangedAccountLevel(checkedUnchangedDir, checkedReferenceDir, synthesizedReferenceDir, unchangedPath)
          .persist(StorageLevel.DISK_ONLY)

        // Combine delta with unchanged reference on account level
        val synthesizedChangedDir = SynthesisIOUtils.makeSynthesizedPathChanged(oxygenConf)
        if (fileUtils.exists(synthesizedChangedDir)) fileUtils.deleteFileOrFolder(synthesizedChangedDir)

        synthesisIOUtils.combineChangedAccountLevel(syntheticEventsWithPinAccountLevelRDD, synthesizedAccountLevelUnchangedDf, synthesizedChangedDir)

        consistencyAccountLevelRDD.unpersist()
        synthesizedAccountLevelUnchangedDf.unpersist()
        syntheticEventsWithPinAccountLevelRDD.unpersist()

      }

      //Sending Updates
      if (newDatasetsToProcess.isDefined)
        newDatasetsToProcess.get.foreach(i => orchestrationHelper.markDatasetAsSuccess(i.jobId(), i.datasetId(), i.location(), target = Option(i.target())))

      if (!defaultOutputStructure) removeDefaultOutputStructure

    }
    else {
      logger.info(s"SYNTHESIS OPERATION SKIPPED!!!!!. No new files came for pinning between runs. To run this stage regardless set 'oxygen.application.processManagement.devMode' to true in config")
    }

    endService()
  }

  def removeDefaultOutputStructure(): Unit = {
    val synthesizedReferenceDir = oxygenConf.oxygen.stage.consistency.get.paths.synthesizedDir
    val appDir = oxygenConf.oxygen.APP_HOME
    val userHomeDir = oxygenConf.oxygen.DATA_HOMES

    //If the app directory do not match the output for the synthetized data
    //we will remove the default structure like app home, data home and keep together and apart
    //directories
    if (appDir.nonEmpty && !synthesizedReferenceDir.contains(userHomeDir) &&
      fileUtils.exists(appDir)) {
      //This will delete the folder with the app name
      fileUtils.deleteFileOrFolder(appDir)
      //This will delete the data home directory
      userHomeDir.foreach(
        path => if(path.contains(synthesizedReferenceDir) && fileUtils.exists(path))
          fileUtils.deleteFileOrFolder(path)
      )
      //This will delete the keep together directory
      val keepTogetherDir = oxygenConf.oxygen.KEEP_TOGETHER_DIRECTORY
      if(fileUtils.exists(keepTogetherDir))
        fileUtils.deleteFileOrFolder(keepTogetherDir)

      //This will delete the keep apart directory
      val keepApartDir = oxygenConf.oxygen.KEEP_APART_DIRECTORY
      if(fileUtils.exists(keepApartDir))
        fileUtils.deleteFileOrFolder(keepApartDir)

      logger.info("Default output structure removed.")
    }

  }

  def emitStockMetric(name: String, key: String, value: Long, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.SYNTHESIS, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: Double, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.SYNTHESIS, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.SYNTHESIS, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

}

