package com.experian.edf.oxygen.statecalculation.engines

import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.stages.StateCalcStageConfiguration
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

import java.io.File
import java.time.{Instant, ZoneId}
import com.experian.edf.oxygen.context._
import com.experian.edf.oxygen.global.domain.statecalc.SyntheticEvents
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.logging.performance.PerformanceLogger.timeIt
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.stage.statecalculation.{FoundationModel, XpmModel}
import com.experian.edf.oxygen.statecalc.StateCalcConfig
import com.experian.edf.oxygen.statecalculation
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationConfiguration
import com.experian.edf.oxygen.statecalculation.config.foundation.FoundationStateConfiguration
import com.experian.edf.oxygen.statecalculation.processors.StateCalculationContext
import com.experian.edf.oxygen.statecalculation.utils.{StateCalcIOUtils, StateCalcUtils, StateCalculationOptions, StateCalculatorContext}


class SparkStateCalculator(appContext: SparkOxygenAppContext) extends AbstractSparkStateCalculator(appContext) {

  private val currentTimestamp = Instant.now.atZone(ZoneId.of("Etc/UTC")).toInstant.toEpochMilli //CHECK with stage timestamp

  val sparkSession = appContext.sparkSession
  val stateCalcAddOns = new StateCalcAddOns(appContext)
  val stateCalcUtils = new StateCalcUtils(fileUtils, appContext)
  val stateCalcIOUtils = new StateCalcIOUtils(fileUtils, appContext)
  private val defaultOutputStructure = oxygenConf.configMap.get("oxygen.features.enable.defaultOutputStructure").getOrElse("false").toBoolean

  stateCalcUtils.displayInfos
  logger.info(s"defaultOutputStructure : $defaultOutputStructure")

  val stateCalcStageConfigs: StateCalcStageConfiguration = oxygenConf.oxygen.stage.stateCalc

  // Read in the configuration for the state-calc domain dictionary
  val foundationConfiguration: List[FoundationStateConfiguration] = StateCalculator.readFoundationConfiguration(stateCalcStageConfigs.paths.foundationConfigurations)

  // Read in the state calculation aggregated dictionary
  val aggregationConfiguration: List[CalculationConfiguration] = StateCalculator.readCalculationConfiguration(stateCalcStageConfigs.paths.calculationConfigurations)

  val KafkaMsg = new KafkaMsg(oxygenConf)
  //private val reasonability = stateCalcStageConfigs.reasonability
  //private val metrics = new ReasonabilityProcess()

  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")

  private val metricsMetadata = Map(MetricsConstants.Keys.GENERATION -> oxygenConf.oxygen.generation.candidate.getOrElse("default"))

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.STATE_CALCULATION,
    source = MetricsConstants.Sources.STATE_CALCULATOR,
    orchestrationSource = oxygenConf.oxygen.stage.stateCalc.orchestration.get.source,
    orchestrationTarget = oxygenConf.oxygen.stage.stateCalc.orchestration.get.target,
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId))

  override def compute(syntheticEvents: RDD[SyntheticEvents], config: Map[String, Any], stateCalculationContext: StateCalculationContext): (RDD[FoundationModel], RDD[XpmModel]) = {
    val stateCalculationConfigurtion = StateCalculationOptions(
      computeAliasInFoundation = stateCalcStageConfigs.aliasEnabled,
      foundationStateEnrichmentProcessor = stateCalculationContext.enrichmentProcessor,
      addParentEventsInAggregation = stateCalcStageConfigs.eventTrackingEnabled
    )
    stateCalculationContext.aggregationFunctions.foreach(stateCalculationConfigurtion.registerAggregationFunction)
    stateCalculationContext.calculationTypes.foreach(stateCalculationConfigurtion.registerCalculationType)
    val scContext = StateCalculatorContext(stateCalculationConfigurtion, foundationConfiguration, aggregationConfiguration, stateCalculationContext.lookup)
    val scContextBroadcast: Broadcast[StateCalculatorContext] = appContext.sc.broadcast(scContext)
    val calculatedState: RDD[(FoundationModel, XpmModel)] = syntheticEvents.mapPartitions(iter => {
      val stateCalculator = new statecalculation.StateCalculator(scContextBroadcast.value)
      iter.map(pinnedSyntheticEvents => {
        calculateState(stateCalculator, pinnedSyntheticEvents)
      })
    })
    val foundation: RDD[FoundationModel] = calculatedState.map(_._1)
    val aggregation: RDD[XpmModel] = calculatedState.map(_._2)

    if (emitStockMetricsEnabled) {
      if (foundation != null) {
        emitStockMetric("foundationCountPerPartition", "foundationCountPerPartition", foundation.count(), metricsMetadata)
      }
      else {
        emitStockMetric("foundationCountPerPartition", "foundationCountPerPartition", 0, metricsMetadata)
      }
      if (aggregation != null) {
        emitStockMetric("aggregationCountPerPartition", "aggregationCountPerPartition", aggregation.count(), metricsMetadata)
      }
      else {
        emitStockMetric("aggregationCountPerPartition", "aggregationCountPerPartition", 0, metricsMetadata)
      }
    }

    //metrics.reasonabilityMetricsTemplateRDD[(FoundationModel)](reasonability, foundation, FoundationModel, "1")

    val aggregationWithHistoric: RDD[XpmModel] = stateCalcAddOns.joinHistoricData(aggregation)
    val statesWithAttributesAndScores: RDD[XpmModel] = stateCalcAddOns.enrichCalculatedState(stateCalculationContext, aggregationWithHistoric)
    if (logger.isInfoEnabled()) {
      foundation.persist(StorageLevel.DISK_ONLY)
      statesWithAttributesAndScores.persist(StorageLevel.DISK_ONLY)
      // todo: As per original comment, can this be removed?
      logger.info("Count Of XPMs : " + aggregation.count()) //TODO: Remove this, Kept for forcing an action to check performance
    }
    (foundation, statesWithAttributesAndScores)

  }

  override def computeFoundation(syntheticEvents: RDD[SyntheticEvents], stateCalculationContext: StateCalculationContext): RDD[FoundationModel] = {
    val scContext = getSCContext(stateCalculationContext)
    val scContextBroadcast = sparkSession.sparkContext.broadcast(scContext)
    val foundation: RDD[FoundationModel] = syntheticEvents.mapPartitions(iter => {
      val stateCalculator = new statecalculation.StateCalculator(scContextBroadcast.value)
      iter.map(pinnedSyntheticEvents => {
        calculateFoundation(stateCalculator, pinnedSyntheticEvents)
      })
    })

    if (emitStockMetricsEnabled) {
      if (foundation != null) {
        emitStockMetric("foundationCountPerPartition", "foundationCountPerPartition", foundation.count(), metricsMetadata)
      }
      else {
        emitStockMetric("foundationCountPerPartition", "foundationCountPerPartition", 0, metricsMetadata)
      }
    }

    //metrics.reasonabilityMetricsTemplateRDD[(FoundationModel)](reasonability, foundation, FoundationModel, "1")

    foundation
  }

  override def computeXpm(foundation: RDD[FoundationModel], stateCalculationContext: StateCalculationContext): RDD[XpmModel] = {
    val scContext = getSCContext(stateCalculationContext)
    val scContextBroadcast = sparkSession.sparkContext.broadcast(scContext)
    val aggregation: RDD[XpmModel] = foundation.map(v => (v.pin, v.data)).mapPartitions(iter => {
      val stateCalculator = new statecalculation.StateCalculator(scContextBroadcast.value)
      iter.map(v => {
        val pin = v._1
        val foundationRec = v._2
        calculateXpm(stateCalculator, pin, foundationRec)
      })
    })

    if (emitStockMetricsEnabled) {
      if (aggregation != null) {
        emitStockMetric("aggregationCountPerPartition", "aggregationCountPerPartition", aggregation.count(), metricsMetadata)
      }
      else {
        emitStockMetric("aggregationCountPerPartition", "aggregationCountPerPartition", 0, metricsMetadata)
      }
    }

    //metrics.reasonabilityMetricsTemplateRDD[(FoundationModel)](reasonability, foundation, FoundationModel, "1")

    val aggregationWithHistoric: RDD[XpmModel] = stateCalcAddOns.joinHistoricData(aggregation)
    val statesWithAttributesAndScores: RDD[XpmModel] = stateCalcAddOns.enrichCalculatedState(stateCalculationContext, aggregationWithHistoric)
    statesWithAttributesAndScores

  }

  def getSCContext(stateCalculationContext: StateCalculationContext): StateCalculatorContext = {
    val stateCalculationConfigurtion = StateCalculationOptions(
      computeAliasInFoundation = stateCalcStageConfigs.aliasEnabled,
      foundationStateEnrichmentProcessor = stateCalculationContext.enrichmentProcessor,
      addParentEventsInAggregation = stateCalcStageConfigs.eventTrackingEnabled
    )
    stateCalculationContext.aggregationFunctions.foreach(stateCalculationConfigurtion.registerAggregationFunction)
    stateCalculationContext.calculationTypes.foreach(stateCalculationConfigurtion.registerCalculationType)
    val scContext = StateCalculatorContext(stateCalculationConfigurtion, foundationConfiguration, aggregationConfiguration, stateCalculationContext.lookup)
    scContext
  }

  override def run(config: Map[String, Any] = null, stateCalculationContext: StateCalculationContext): Unit = {

    startService()

    logger.info("Running spark state calculator ....")

    val outputFormat = oxygenConf.configMap.getOrElse(StateCalcConfig.OUTPUT_FORMAT, "file")
    val writeFormat: String = StateCalcIOUtils.getWriteFormat(oxygenConf)

    val deltaModeAccountLevel = oxygenConf.configMap.get("oxygen.stage.stateCalculation.deltaModeAccountLevel").getOrElse("false").toBoolean
    val deltaMode = if (deltaModeAccountLevel) false else oxygenConf.configMap.get("oxygen.stage.stateCalculation.deltaMode").getOrElse("false").toBoolean
    val doesComputeXpm = oxygenConf.configMap.get("oxygen.stage.stateCalculation.doesComputeXpm").getOrElse("true").toBoolean
    val doesArchive = oxygenConf.configMap.get("oxygen.stage.stateCalculation.doesArchive").getOrElse("true").toBoolean
    val doesSaveFoundation = oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_FOUNDATION)
    val doesSaveAdditionalFoundation = oxygenConf.oxygen.isFeatureActive(Constants.features.ADDITIONAL_STATECALC_OUTPUT_FOUNDATION)
    val doesSaveCalculated = oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_CALCULATION)
    val doesSaveCalculatedTsv = oxygenConf.oxygen.isFeatureActive(Constants.features.STATECALC_OUTPUT_AS_TSV)
    val doesSaveCalculatedHbase = outputFormat.equalsIgnoreCase("hbase") || writeFormat.equalsIgnoreCase("hbase")

    logger.error("deltaMode " + deltaMode.toString)
    logger.error("deltaModeAccountLevel " + deltaModeAccountLevel.toString)
    logger.error("doesComputeXpm " + doesComputeXpm.toString)
    logger.error("doesArchive " + doesArchive.toString)
    logger.error("doesSaveFoundation " + doesSaveFoundation.toString)
    logger.error("doesSaveAdditionalFoundation " + doesSaveAdditionalFoundation.toString)
    logger.error("doesSaveCalculated " + doesSaveCalculated.toString)

    val instructions = orchestrator.retrieveAllDatasetsToProcess()
    if (appContext.config.oxygen.DEV_MODE || (instructions.isDefined && instructions.get.nonEmpty)) {
      val syntheticEvents: RDD[SyntheticEvents] = if (deltaModeAccountLevel) {
        stateCalcIOUtils.retrieveSyntheticEventsAsDSDeltaAccountLevel
      } else if (deltaMode) {
        stateCalcIOUtils.retrieveSyntheticEventsAsDSDelta
      } else {
        stateCalcIOUtils.retrieveSyntheticEventsAsDS
      }

//      val (foundation, aggregation) = compute(syntheticEvents, config, stateCalculationContext)

      val foundation = computeFoundation(syntheticEvents, stateCalculationContext)
      if (doesSaveFoundation || doesSaveAdditionalFoundation) foundation.persist(StorageLevel.DISK_ONLY)
      val aggregation = if (doesComputeXpm) {
        computeXpm(foundation, stateCalculationContext).persist(StorageLevel.DISK_ONLY)
      } else {
        sparkSession.sparkContext.emptyRDD[XpmModel]
      }

      // todo: foundation is persisted in the compute method (sometimes), aggregation is persist here
      // and both are unpersisted in the saveState method.
      // It feels like it would be much safer/better to have a single block responsible for both persisting
      // and unpersisting RDDs - suggestion is that we make saveState persist both and not have them
      // persisted elsewhere
      if (deltaModeAccountLevel || deltaMode) {
        if (!doesComputeXpm) {
          stateCalcIOUtils.saveStateDelta(foundation, aggregation, doesComputeXpm, doesArchive)
        } else if (!aggregation.isEmpty) {
          stateCalcIOUtils.saveStateDelta(foundation, aggregation, doesComputeXpm, doesArchive)
        } else {
          logger.error("Statecalc calculated-delta is empty.")
        }
      } else {
        stateCalcIOUtils.saveState(foundation, aggregation, doesComputeXpm, doesArchive)
      }

      foundation.unpersist()
      aggregation.unpersist()

      // Save unchanged reference v2
      if (deltaModeAccountLevel || deltaMode) {
        val timestamp = String.valueOf(Instant.now().getEpochSecond)
        if (doesArchive && aggregation.isEmpty) stateCalcIOUtils.archiveAndCleanStateCalcDir(timestamp)

        val candidateGenerationHome = oxygenConf.configMap.get("oxygen.application.paths.candidateGenerationHome").getOrElse("")
        val unchangedPath = candidateGenerationHome + File.separator + "consistency" + File.separator + "synthesized" + File.separator + "synthesized-unchanged"
        if (fileUtils.exists(unchangedPath)) {
          val foundationUnchangedDir = stateCalcIOUtils.makeFoundationDeltaPath("unchanged")
          val additionalFoundationUnchangedDir = stateCalcIOUtils.makeAdditionalFoundationDeltaPath("unchanged")
          val calculatedUnchangedDir = StateCalcIOUtils.makeCalculatedDeltaPath("unchanged", oxygenConf)
          if (doesArchive) {
            if (fileUtils.exists(foundationUnchangedDir)) fileUtils.deleteFileOrFolder(foundationUnchangedDir)
            if (fileUtils.exists(additionalFoundationUnchangedDir)) fileUtils.deleteFileOrFolder(additionalFoundationUnchangedDir)
            if (fileUtils.exists(calculatedUnchangedDir)) fileUtils.deleteFileOrFolder(calculatedUnchangedDir)
          }

          val referenceRootDir = oxygenConf.configMap.get("oxygen.stage.stateCalculation.paths.statecalcReferenceDir").getOrElse("").stripSuffix(File.separator)
          val foundationReferenceDir = referenceRootDir + File.separator + "foundation" + File.separator + "*" + File.separator + "*"
          val additionalFoundationReferenceDir = referenceRootDir + File.separator + "additionalFoundation" + File.separator + "*" + File.separator + "*"
          val calculatedReferenceDir = referenceRootDir + File.separator + "calculated" + File.separator + "*" + File.separator + "*"

          if (referenceRootDir.stripSuffix(File.separator + "statecalc") == candidateGenerationHome) throw new Exception("Candidate and Reference generations are the same!")
          stateCalcIOUtils.getAndSaveUnchangedV2(foundationUnchangedDir, additionalFoundationUnchangedDir,
            calculatedUnchangedDir, foundationReferenceDir, additionalFoundationReferenceDir, calculatedReferenceDir,
            unchangedPath, doesComputeXpm)
        }

      }

      if (instructions.isDefined)
        instructions.get.foreach(i => orchestrationHelper.markDatasetAsSuccess(i.jobId(), i.datasetId(), i.location()))
    } else {
      logger.info("STATECALCULATION OPERATION SKIPPED!!!. No new files came consistency check in between runs. To run this stage regardless set 'oxygen.application.processManagement.devMode' to true in config")
    }

    if (!defaultOutputStructure) removeDefaultOutputStructure

    endService()
  }


  def removeDefaultOutputStructure(): Unit = {
    val stateCalc = oxygenConf.oxygen.stage.stateCalc

    val foundationOutput = stateCalc.paths.foundationOutput
    val calculationOutput = stateCalc.paths.calculatedOutput
    val userHomeDir = oxygenConf.configMap.getOrElse("oxygen.application.paths.userHome", "")

    if (userHomeDir != "" &
      ((!calculationOutput.contains(userHomeDir)) | (!foundationOutput.contains(userHomeDir))) &
      fileUtils.exists(userHomeDir)) {
      fileUtils.deleteFileOrFolder(userHomeDir)
      logger.info("Default output structure removed.")
    }
  }


  def emitStockMetric(name: String, key: String, value: Long, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.STATE_CALCULATION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: Double, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.STATE_CALCULATION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.STATE_CALCULATION, orchestrationContext.runId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }


  private def calculateState(stateCalculator: statecalculation.StateCalculator, pinnedSyntheticEvents: SyntheticEvents): (FoundationModel, XpmModel) = {
    val foundationStateEvents = timeIt(stateCalculator.computeFoundationState(pinnedSyntheticEvents))
    val aggregateStateEvents = timeIt(stateCalculator.computeCalculationState(foundationStateEvents.result._1))
    if (logger.isDebugEnabled)
      logger.debug(s"""{ "pin": ${pinnedSyntheticEvents.pin}, "foundation": ${foundationStateEvents.asWords}, "aggregation": ${aggregateStateEvents.asWords}, "total": ${foundationStateEvents.asMillis + aggregateStateEvents.asMillis} }""")
    (FoundationModel(pinnedSyntheticEvents.pin(), foundationStateEvents.result._1,foundationStateEvents.result._2), XpmModel(pinnedSyntheticEvents.pin(), aggregateStateEvents.result))
  }

  private def calculateFoundation(stateCalculator: statecalculation.StateCalculator, pinnedSyntheticEvents: SyntheticEvents): FoundationModel = {
    if (logger.isDebugEnabled) {
      val foundationStateEvents = timeIt(stateCalculator.computeFoundationState(pinnedSyntheticEvents))
      logger.debug(s"""{ "pin": ${pinnedSyntheticEvents.pin}, "foundation": ${foundationStateEvents.asWords} }""")
      FoundationModel(pinnedSyntheticEvents.pin(), foundationStateEvents.result._1,foundationStateEvents.result._2)
    } else {
      val foundationStateEvents = stateCalculator.computeFoundationState(pinnedSyntheticEvents)
      FoundationModel(pinnedSyntheticEvents.pin(), foundationStateEvents._1,foundationStateEvents._2)
    }
  }

  private def calculateXpm(stateCalculator: statecalculation.StateCalculator, pin: Long, foundationRecord: List[statecalculation.FoundationRecord]): XpmModel = {
    if (logger.isDebugEnabled) {
      val aggregateStateEvents = timeIt(stateCalculator.computeCalculationState(foundationRecord))
      logger.debug(s"""{ "pin": ${pin}, "aggregation": ${aggregateStateEvents.asWords} }""")
      XpmModel(pin, aggregateStateEvents.result)
    } else {
      val aggregateStateEvents = stateCalculator.computeCalculationState(foundationRecord)
      XpmModel(pin, aggregateStateEvents)
    }
  }

}