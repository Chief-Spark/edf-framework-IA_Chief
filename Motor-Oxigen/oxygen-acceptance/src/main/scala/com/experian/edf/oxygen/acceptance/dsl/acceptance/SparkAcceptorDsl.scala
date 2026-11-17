package com.experian.edf.oxygen.acceptance.dsl.acceptance


import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.acceptance.engines.context.SparkOxygenAppContext
import com.experian.edf.oxygen.acceptance.ingestion.IngestionConfigManager
import com.experian.edf.oxygen.acceptance.ingestion.config.DatasetAcceptanceConfig
import com.experian.edf.oxygen.configuration.configs.IngestionConfiguration
import com.experian.edf.oxygen.global.processmngt.ProcessInstruction
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import org.apache.commons.io.FilenameUtils.{getBaseName, getName}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

class SparkAcceptorDsl(appContext: SparkOxygenAppContext) extends AcceptorDsl(appContext) {

  val ingestionConfig: IngestionConfiguration = appContext.config.oxygen.stage.ingestion.get
  val ingestionConfigManager: IngestionConfigManager = IngestionConfigManager.create(ingestionConfig.fileMapping.get)


  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext("Acceptance", "Acceptor", oxygenConf.oxygen.ACCEPTANCE_ORCHESTRATION.source, oxygenConf.oxygen.ACCEPTANCE_ORCHESTRATION.target, oxygenConf, Option(appContext.sc.applicationId))

  override def runInternal(): Unit = {
    startService()
    getOneDatasetAndAccept()
    endService()
  }

  @tailrec
  private def getOneDatasetAndAccept(): Unit = {
    val datasetToAccept = orchestrator.retrieveOneDatasetToProcess()
    if (datasetToAccept.isDefined) {
      val processInstruction = datasetToAccept.get
      val accepted = processDatasetAcceptance(processInstruction)
      accepted match {
        case Failure(exception) =>
          logger.warn(s"Not able to validate dataset ${processInstruction.location} - ${exception.getMessage}")
        case _ =>
      }
      getOneDatasetAndAccept()
    } else
      logger.info("All datasets has been accepted. Exiting..!!")
  }

  def processDatasetAcceptance(processInstruction: ProcessInstruction): Try[Boolean] = {
    val fileNameOnly: String = getBaseName(processInstruction.getLocation)
    val acceptanceConfigOption: Option[DatasetAcceptanceConfig] = ingestionConfigManager.getDatasetAcceptanceConfig(fileNameOnly)
    acceptanceConfigOption match {
      case Some(acceptanceConfig) =>
        orchestrationHelper.markDatasetAsInProgress(processInstruction)
        val sparkDatasetAcceptor = new SparkDatasetAcceptor(appContext = appContext, datasetAcceptanceConfig = acceptanceConfig, orchestrationHelper = orchestrationHelper, lookupBC = OxygenLookupService.getServiceB, fileUtils)
        val automaticRejection = sparkDatasetAcceptor.shouldAutomaticReject(processInstruction)
        if(automaticRejection){
          Success(false)
        } else {
          val processed = sparkDatasetAcceptor.processFile(processInstruction)
          Success(processed)
        }
      case None =>
        logger.warn(s"No Acceptance Configuration found in the Ingestion mapping for ${processInstruction.location()}")
        orchestrationHelper.markDatasetAsFailed(processInstruction, s"No Validation Configuration found in the Ingestion mapping for ${processInstruction.location()}")
        Failure(new OxygenException(s"Acceptance: No Acceptance Configuration found in the Ingestion mapping for ${processInstruction.location()}"))
    }

  }

}
