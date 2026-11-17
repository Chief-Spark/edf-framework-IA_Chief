package com.experian.edf.oxygen.validation.engines

import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.module.logging.metrics.MetricsConstants
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import org.slf4j.LoggerFactory._


/**
 *
 * @param appContext
 */
class SparkDiscoverer(appContext: SparkOxygenAppContext) extends Discoverer(appContext) {

  @transient override lazy val logger = getLogger(this.getClass)

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(MetricsConstants.Actions.DISCOVERY, MetricsConstants.Sources.DISCOVERER, None, None, oxygenConf, Option(appContext.sc.applicationId))
  val sparkSession = appContext.sparkSession // Spark session

  override def run(): List[String] = {

    startService()

    //metricsEmitter.emitCritical(
    //  MetricEventsFactory.stageInfo.htStageStarted(
    //    orchestrationContext.runId, MetricsConstants.Actions.DISCOVERY, sparkSession.conf.get("spark.app.id") , map.toMap))

    val files = try {
      discover(oxygenConf.oxygen.DATA_HOMES)
    } finally {
      orchestrator.close()
    }

    endService()

    files
  }

  def discover(dataHomes: List[String]): List[String] = {
    val incomingFiles: List[(String, String)] = dataHomes.flatMap(home => {
      fileUtils.listFilePathAndName(home)
    })
    incomingFiles.foreach(filenameTuples => {
      val fullPathFileName = filenameTuples._1
      logger.debug("Discovered file from home: " + fullPathFileName)
      if (!fullPathFileName.endsWith("edftmp") && !fullPathFileName.endsWith("DS_Store"))
        orchestrationHelper.discoverDataset(fullPathFileName, "File Discovered", target = Option(oxygenConf.oxygen.DISCOVERY_ORCHESTRATION.target.get.head))
    })
    incomingFiles.map(_._1)
  }

}