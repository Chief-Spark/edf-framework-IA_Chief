package com.experian.edf.oxygen.acceptance.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.{AcceptanceCondition, AcceptanceElement, LookupMapping}
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.global.processmngt.ProcessInstruction
import com.experian.edf.oxygen.global.processmngt.constants.PmConstants
import com.experian.edf.oxygen.global.utils.{IdGenerator, OxygenCollectionsUtils, ResourceUtils}
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.utils._
import org.apache.commons.io.FilenameUtils._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql
import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.commons.lang.StringEscapeUtils.escapeJava

import scala.collection.mutable
import java.io.{File, FileNotFoundException}
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import com.experian.edf.oxygen.acceptance.ContextualOverride
import com.experian.edf.oxygen.acceptance.engines.dsl.{AcceptanceEvaluator, AcceptanceStatement}
import com.experian.edf.oxygen.acceptance.util.{AggregatesParserDF, ExternalActionParser, RecordValidationParser}
import com.experian.edf.oxygen.domain.Period
import com.experian.edf.oxygen.domain.internal.{TaggedCuratedEvent, TaggedValidatedEvent}
import com.experian.edf.oxygen.module.logging.metrics.{MetricEventsFactory, MetricsConstants}
import org.apache.commons.io.FilenameUtils

class SparkAcceptor(appContext: SparkOxygenAppContext) extends Acceptor(appContext){

  val acceptanceTimestamp = System.currentTimeMillis() // Generate and use the timestamp for the whole acceptance stage run
  // val InProcessSuffix = Constants.INPROCESS_SUFFIX // The inprocess suffix for files
  val CompletedProcessingDir = oxygenConf.oxygen.PROCESSED_VALIDATED_DIRECTORY + File.separator
  //Constants.VALIDATION_COMPLETED_PROCESSING
  val sparkSession = appContext.sparkSession // Spark session

  import sparkSession.implicits._

  val emptyDataFrame = appContext.sparkSession.sqlContext.emptyDataFrame
  val emptyRDDTaggedValidatedEvent = appContext.sc.emptyRDD[TaggedValidatedEvent]

  // TODO: Change the acceptanceValues call so  that it returns a AcceptanceElement, not ProcessElement.
  val acceptanceConfig = oxygenConf.oxygen.acceptanceValues // acceptance config section from config file
  val acceptanceFileConditions = oxygenConf.oxygen.acceptanceFileConditions //Used for new syntax rule mapping
  val validatedHome = oxygenConf.oxygen.VALIDATION_ACCEPTED_DIRECTORY // Data home for acceptance phase
  val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT // JSON, Avro, Parquet???
  val readFormat = oxygenConf.oxygen.ACCEPTANCE_READ_WRITE_FORMAT.input.getOrElse(storageFormat)
  val writeFormat = oxygenConf.oxygen.ACCEPTANCE_READ_WRITE_FORMAT.output.getOrElse(storageFormat)
  val acceptedFilesOutputDir = oxygenConf.oxygen.ACCEPTED_DIRECTORY + File.separator // Accepted file DIR
  val acceptanceInputDir = oxygenConf.oxygen.VALIDATED_DIRECTORY + File.separator //Acceptance input Dir
  val acceptedRecordsTempDir = oxygenConf.oxygen.APP_HOME + File.separator + "temp" + File.separator + "accepted" + File.separator
  val rejectedFilesOutputDir = oxygenConf.oxygen.REJECTED_FILES_DIRECTORY + File.separator // Rejected files DIR
  val rejectedRecordsTempDir = oxygenConf.oxygen.APP_HOME + File.separator + "temp" + File.separator + "rejected" + File.separator
  val rejectedRecordsOutputDir = oxygenConf.oxygen.REJECTED_RECORDS_DIRECTORY + File.separator // Rejected files DIR
  private val defaultOutputStructure = oxygenConf.configMap.get("oxygen.features.enable.defaultOutputStructure").getOrElse("false").toBoolean
  private val runningOnAWS = oxygenConf.configMap.get("oxygen.features.enable.runningAWS").getOrElse("false").toBoolean


  val edgenodeHostname = java.net.InetAddress.getLocalHost.getHostName
  val pid = ResourceUtils.getProcessID
  val puTopic = oxygenConf.oxygen.getKafkaLoggerTopic.get("puTopic").getOrElse("")
  val metricTopic = oxygenConf.oxygen.getKafkaLoggerTopic.get("metricTopic").getOrElse("")
  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")
  val kafkaMsg = new KafkaMsg(oxygenConf);

  //private val reasonability = appContext.config.oxygen.ACCEPTANCE_REASONABILITY
  //private val metrics = new ReasonabilityProcess()
  //private val currentTimestamp = Instant.now.atZone(ZoneId.of("Etc/UTC")).toInstant.toEpochMilli  //CHECK with stage timestamp

  lazy val taggedCuratedEventsForContextual = readNonDeletedCuratedEvent.cache

  //val KafkaMsg = new KafkaMsg(oxygenConf)
  private val ioHelper = new AcceptanceIOUtils(appContext, fileUtils)

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.ACCEPTANCE,
    source = MetricsConstants.Sources.ACCEPTOR,
    orchestrationSource = oxygenConf.oxygen.ACCEPTANCE_ORCHESTRATION.source,
    orchestrationTarget = oxygenConf.oxygen.ACCEPTANCE_ORCHESTRATION.target,
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId))


  @throws(classOf[OxygenException])
  override def run(incremental: Boolean = true): Any = {

    startService()

    getOneDatasetAndAccept()

    logger.warn(s"defaultOutputStructure : $defaultOutputStructure")
    if (!defaultOutputStructure) removeDefaultOutputStructure()

    endService()

  }

  // @tailrec
  private def getOneDatasetAndAccept(): Unit = {
    val datasetToAccept = getFile(acceptanceInputDir)
    datasetToAccept.foreach(file =>{
      logger.info(s"Processing file ${file._2}")
      processDatasetAcceptance(createProcessInstruction(file))
    })
      logger.info("All datasets has been accepted. Exiting..!!")
  }

  private def createProcessInstruction(file: (String, String)) = {
    val lineage = readFile(file._1).first().lineage // TODO: Need to find a better way for doing this.
    val processInstruction: ProcessInstruction = new ProcessInstruction()
    processInstruction.setDatasetId(lineage.datasetId)
    processInstruction.setTarget(acceptedFilesOutputDir)
    processInstruction.setLocation(file._1)
    processInstruction.setJobId(pid.toString)
    processInstruction.setStage(MetricsConstants.Actions.ACCEPTANCE)
    processInstruction
  }

  private def processDatasetAcceptance(processInstruction: ProcessInstruction) = {

    // orchestrator.completeOneProcessInstruction()

    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    logger.debug(escapeJava("Getting file to accept:" + fullPathFileName))

    // Rename file to .inprocess and start processing. If another acceptor job has already picked it up, move to next file in list
        val acceptanceElement = Try(
          acceptanceConfig.find(elem => removeFileExtension(fullPathFileName) matches elem._1).get._2.asInstanceOf[AcceptanceElement]
        )
        acceptanceElement match {
          case Success(config) => {
            processFileName(processInstruction, config) match {
              case Success(true) => {
                logger.info("Acceptance stage success success")
              }
              case Failure(ex) => {
                logger.error(s"Error in Acceptance: ", ex)
                orchestrationHelper.markDatasetAsError(processInstruction.getJobId, processInstruction.datasetId(), fullPathFileName, "Acceptance stage error", ex.getMessage, target = Option(processInstruction.target()))
              }

          case Failure(ex) => {
            logger.error(s"No acceptance configuration found for $fullPathFileName")
          }
        }
      } // End Acceptance match Success(true)
    } // End startProcessingFile
  }

  private[this] def removeFileExtension(fullPathFileName: String): String = {
    if (fullPathFileName.contains("errorIn"))
      removeExtension(removeExtension(fullPathFileName))
    else
      removeExtension(fullPathFileName)
  }

  private[this] def processFileName(processInstruction: ProcessInstruction, config: AcceptanceElement): Try[Boolean] = {
    val fileNameOnly: String = getName(processInstruction.location())

    val fileAcceptExpression = config.fileActions.get.accept
    // If it is errorInHeader / errorInFooter / errorInHistoricAttribute then reject.
    // If it is emptyBody error, and if accept expression does not have isEmptyBodyFile, then reject.
    val automaticRejection =  shouldAutomaticReject(fileNameOnly, fileAcceptExpression)
    val customReasonForAutomaticRejectionEnabled: Boolean = if (config.customReasonForAutomaticRejectionEnabled.isDefined) config.customReasonForAutomaticRejectionEnabled.get else false;
    if ( automaticRejection) {
      // reject file
      val customRejectResult:Try[Boolean] =  Try({if (customReasonForAutomaticRejectionEnabled ) {
        val ret = handleCustomAutomaticReject(processInstruction, config);
        ret
      } else false})

      customRejectResult match {
        case Success(true) => {
          logger.debug("handleCustomAutomaticReject") // Do nothing. File has been rejected
          Success(true)
        }
        case _ => {
          val errorMsg = getExtension(removeExtension(fileNameOnly))
          logger.info(escapeJava(s"File rejected due to --- " + errorMsg))
          moveFileToRejected(processInstruction, errorMsg)
        }
      }

    }
    else {
      //process file
      processFile(processInstruction, config)
    }
  }

  private def shouldAutomaticReject(fileNameOnly: String, fileAcceptExpression: Option[String]): Boolean = {
    val shouldReject = if (fileNameOnly.contains(Constants.ERROR_IN_HEADER) ||
      fileNameOnly.contains(Constants.ERROR_IN_FOOTER) ||
      fileNameOnly.contains(Constants.ERROR_IN_FILENAME) ||
      fileNameOnly.contains(Constants.ERROR_IN_HISTORIC_ATTR) ||
      (fileNameOnly.contains(Constants.ERROR_IN_EMPTY_FILE_BODY) && (!fileAcceptExpression.get.contains(Constants.IS_EMPTY_BODY_FILE)))
    ) true
    else false

    shouldReject
  }

  private def handleCustomAutomaticReject(processInstruction: ProcessInstruction , config: AcceptanceElement): Boolean ={

    val fullPathFileName: String = processInstruction.location()
    val lookupMap = buildReasonCodeSeverityMap(config.lookupMapping, acceptanceFileConditions.toMap)
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(acceptanceFileConditions.toMap, lookupMap)
    val validatedEvents = readFile(fullPathFileName, config).cache
    val qsStatsDF = AggregatesParserDF.createQSDataFrame(appContext.sparkSession, validatedEvents)
    handleFileRejection(qsStatsDF, config, processInstruction, evaluator)  match {
      case Success(true) => {
        logger.debug("handleCustomAutomaticReject") // Do nothing. File has been rejected
        return true
      }
      case _ => return false
    }
  }

  private[this] def processFile(processInstruction: ProcessInstruction, config: AcceptanceElement): Try[Boolean] = {
    val fullPathFileName: String = processInstruction.location()
    val lookupMap = buildReasonCodeSeverityMap(config.lookupMapping, acceptanceFileConditions.toMap)
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(acceptanceFileConditions.toMap, lookupMap)
    val emptyDF = appContext.sparkSession.emptyDataFrame
    val emptyDS = appContext.sparkSession.emptyDataset[TaggedValidatedEvent]

    //file exists validation
    val fileExists = fileUtils.exists(fullPathFileName)
    if (!fileExists){
      return Failure(new Exception(s"file $fullPathFileName do not exists"))
    }

    //Handle manual action overrides upfront. Manual Reject first and then manual accept.
    handleManualAction(emptyDF, emptyDS, config, processInstruction, evaluator, "REJECT") match {
      case Success(true) => {
        logger.info("manual reject expression evaluated to true") // Do nothing. File has been manually rejected
        return Success(true)
      }
      case _ =>
    }
    // Read the validated files as dataframe. Cache it.
    val validatedEvents = readFile(fullPathFileName, config).cache
    val qsStatsDF = AggregatesParserDF.createQSDataFrame(appContext.sparkSession, validatedEvents)
    handleManualAction(qsStatsDF, validatedEvents, config, processInstruction, evaluator, "ACCEPT") match {
      case Success(true) => {
        logger.info("manual accept expression evaluated to true")
        return Success(true)
      }
      case _ =>
    }
    // Handle file rejection. If the file is to be rejected, no point processing further
    logger.debug(s"acceptanceFileConditions configuration $acceptanceFileConditions")
    handleFileRejection(qsStatsDF, config, processInstruction, evaluator) match {
      case Success(true) => {
        logger.debug("reject expression evaluated to true") // Do nothing. File has been rejected
        Success(true)
      }

//     case _ => handleFileManualReview(qsStatsDF, config, processInstruction, evaluator) match {
//        case Success(true) => {
//          logger.debug("manual review expression evaluated to true") //Nothing further to do. File needs manual review.
//          Success(true)
//        }
        case _ => handleFileAcceptance(qsStatsDF, config, processInstruction, validatedEvents, evaluator)
//      }
    }
  }

  def handleManualAction(qsStatsDF: sql.DataFrame, validatedEvents: Dataset[TaggedValidatedEvent],
                         config: AcceptanceElement, processInstruction: ProcessInstruction, evaluator: AcceptanceEvaluator, action: String): Try[Boolean] = {
    Try({
      val fileId: String = processInstruction.datasetId()
      logger.info(s"Processing dataset file id $fileId with manual $action checks")
      val manualActionExpr = action match {
        case "REJECT" => config.fileActions.get.manualReject
        case "ACCEPT" => config.fileActions.get.manualAccept
        case _  => None
      }
      if (manualActionExpr.isEmpty) {
        logger.info(s"Skipping manual action $action check for dataset file id $fileId and returning false since it is not configured.")
        return Success(false)
      }
      //Evaluate using new Acceptance DSL syntax with default evaluation as false.
      val exec = evaluator.compile(manualActionExpr.get)
      val resultLog: StringBuilder = StringBuilder.newBuilder
      val manualAction = if (exec.isDefined) {
        val aggregatesParserDF = new ExternalActionParser(oxygenConf, appContext.sparkSession.sqlContext, qsStatsDF, fileId)
        evalExpressionWithLog(evaluator, aggregatesParserDF, exec, resultLog)
      } else {
        logger.info(s"Evaluating manual $action configuration failed with new syntax. Please check syntax. manual$action evaluation result set to false.")
        false
      }
      val manualActMsg = s"Manual $action expression ${manualActionExpr.get} evaluated to: $manualAction. Reason Codes: $resultLog"
      logger.info(s"$manualActMsg.")
      var manualActionFileMoveResult = "successful"
      if (manualAction)  {
        action match {
          case "REJECT" =>
            manualActionFileMoveResult = if (moveFileToRejected(processInstruction, manualActMsg).getOrElse(false)) "successful" else "failed"
          case "ACCEPT" =>
            manualActionFileMoveResult = if (handleRecordLevelAcceptance(config, processInstruction, validatedEvents, config.fileActions.get.accept, manualActMsg)) "successful" else "failed "
        }
        logger.info(s"Manual $action file move $manualActionFileMoveResult.")
      }
      manualAction
    })
  }

  private[this] def handleFileRejection(qsStatsDF: sql.DataFrame, config: AcceptanceElement, processInstruction: ProcessInstruction, evaluator: AcceptanceEvaluator): Try[Boolean] = {
    Try({
      val fileId: String = processInstruction.getDatasetId
      logger.info(s"Processing dataset file id $fileId with rejection checks")
      val aggregatesParserDF = new ExternalActionParser(oxygenConf, appContext.sparkSession.sqlContext, qsStatsDF, fileId)
      val fileRejectionExpression = config.fileActions.get.reject
      val exec = evaluator.compile(fileRejectionExpression.get)
      val resultLog: StringBuilder = StringBuilder.newBuilder
      val shouldReject = if (exec.isDefined) {
        evalExpressionWithLog(evaluator, aggregatesParserDF, exec, resultLog)
      } else {
        logger.info("Evaluating reject configuration using older syntax. Reason Codes will not be logged.")
        isFileActionValid(fileRejectionExpression, aggregatesParserDF)
      }
      val rejectMsg = s"File rejection expression ${fileRejectionExpression.get} evaluated to: $shouldReject. Reason Codes: $resultLog"
      logger.info(s"$rejectMsg.")
      if (shouldReject) moveFileToRejected(processInstruction, rejectMsg)
      shouldReject
    })
  }

  private def evalExpressionWithLog(evaluator: AcceptanceEvaluator, aggregatesParserDF: ExternalActionParser, exec: Option[AcceptanceStatement], resultLog: StringBuilder): Boolean = {
    val (shouldRejectLog, reasons) = evaluator.execute(exec.get, acceptanceFileConditions.values.toList, aggregatesParserDF)
    val log = reasons.filter(_.isLogged).map(reason => s"${reason.reasonCode}: ${reason.result}")
    resultLog.append(log.mkString(" "))
    shouldRejectLog
  }

//  private[this] def handleFileManualReview(qsStatsDF: sql.DataFrame, config: AcceptanceElement,
//                                           processInstruction: String, evaluator: AcceptanceEvaluator): Try[Boolean] = {
//    Try({
//      val fileId: String = getName(processInstruction)
//      val fullPathFileName: String = processInstruction
//      val manualReviewExpression = config.fileActions.get.manualReview
//      if (manualReviewExpression.isEmpty) {
//        logger.info(s"Skipping manual review check for dataset file id $fileId and returning false since it is not configured.")
//        return Success(false)
//      }
//      logger.info(s"Processing dataset file id $fileId with manual checks")
//      val aggregatesParserDF = new ExternalActionParser(oxygenConf, appContext.sparkSession.sqlContext, qsStatsDF, fileId)
//      val exec = evaluator.compile(manualReviewExpression.get)
//      val resultLog: StringBuilder = StringBuilder.newBuilder
//      val shouldManualReview = if (exec.isDefined) {
//        evalExpressionWithLog(evaluator, aggregatesParserDF, exec, resultLog)
//      } else {
//        logger.info("Evaluating manual review check failed with new syntax. Please check syntax. Manual review evaluation result set to false.")
//        false
//      }
//      val manualReviewMsg = s"Manual Review expression ${manualReviewExpression.get} evaluated to: $shouldManualReview. Reason Codes: $resultLog"
//      logger.info(s"$manualReviewMsg.")
//      if (shouldManualReview) moveFileToManualReview(processInstruction, fullPathFileName, manualReviewMsg)
//      shouldManualReview
//    })
//  }

  private[this] def handleFileAcceptance(qsStatsDF: sql.DataFrame, config: AcceptanceElement,
                                         processInstruction: ProcessInstruction,
                                         validatedEvents: Dataset[TaggedValidatedEvent],
                                         evaluator: AcceptanceEvaluator): Try[Boolean] = {
    Try({
      val fullPathFileName: String = processInstruction.location()
      val fileNameOnly: String = getName(fullPathFileName)
      val fileId: String = processInstruction.datasetId()
      val aggregatesParserDF = new ExternalActionParser(oxygenConf, appContext.sparkSession.sqlContext, qsStatsDF, fileId)
      val fileAcceptanceExpression = config.fileActions.get.accept
      val exec = evaluator.compile(fileAcceptanceExpression.get)
      val resultLog: StringBuilder = StringBuilder.newBuilder
      // if fileName has errorInEmptyFileBody and moved here, we don't check accept expression and pass through as true
      val shouldAccept = if (fileNameOnly.contains("errorInEmptyFileBody") && fileAcceptanceExpression.get.contains("isEmptyBodyFile")) true
      else {
        if (exec.isDefined) {
          evalExpressionWithLog(evaluator, aggregatesParserDF, exec, resultLog)
        } else {
          logger.info("Evaluating accept configuration using older syntax. Reason Codes will not be logged.")
          isFileActionValid(fileAcceptanceExpression, aggregatesParserDF)
        }
      }
      val acceptMsg = s"File acceptance expression: ${fileAcceptanceExpression.get}, Evaluated to: $shouldAccept. Reason Codes: $resultLog"
      logger.info(s"$acceptMsg.")
      if (shouldAccept) {

        handleRecordLevelAcceptance(config, processInstruction, validatedEvents, fileAcceptanceExpression, acceptMsg)

      }
      else {

        // TODO: Check where to move to manual review
        ioHelper.saveAccepteddData(validatedEvents, fullPathFileName, false)
        // moveFileToManualReview(processInstruction, fullPathFileName)
      }
    })
  }

  private def handleRecordLevelAcceptance(config: AcceptanceElement, processInstruction: ProcessInstruction, validatedEvents: Dataset[TaggedValidatedEvent], fileAcceptanceExpression: Option[String], comment : String): Boolean = {
    val fullPathFileName: String = processInstruction.location()
    val fileNameOnly: String = getName(fullPathFileName)
    val fileId: String = processInstruction.datasetId()
    // Split the file into accepted / rejected records

    //logger.info(s"FileId: $fileId")
    //logger.info(s"FileIdExtn: $fileIdExtn")

    val (acceptedRecordsDS, rejectedRecordsDS) =
    // if empty body, we return one record(original file) for accept, and empty record for reject
      if (fileNameOnly.contains("errorInEmptyFileBody") && fileAcceptanceExpression.get.contains("isEmptyBodyFile")) {
        (validatedEvents, emptyRDDTaggedValidatedEvent.toDS())
      } else getAcceptAndRejectedRecords(validatedEvents)

    val acceptedRecordCnt = acceptedRecordsDS.count
    val rejectedRecordCnt = rejectedRecordsDS.count
    val totalRecordCnt = acceptedRecordCnt + rejectedRecordCnt
    logger.info(s"Record level acceptance of $fileNameOnly acceptedRecordCnt = $acceptedRecordCnt rejectedRecordCnt = $rejectedRecordCnt")
    kafkaMsg.sendRecordWithProperties(oxygenConf, metricTopic, new LegacyAcceptedRecordsLoggingEvent(fileId, acceptedRecordCnt).toString, fileId)
    kafkaMsg.sendRecordWithProperties(oxygenConf, metricTopic, new LegacyRejectedRecordsLoggingEvent(fileId, rejectedRecordCnt).toString, fileId)

    val map = mutable.HashMap[String, Any]()
    if (emitStockMetricsEnabled) {
      emitStockMetric("acceptedRecords", "acceptedRecords", acceptedRecordCnt, fileId, map.toMap)
      emitStockMetric("rejectedRecords", "rejectedRecords", rejectedRecordCnt, fileId, map.toMap)
      emitStockMetric("totalRecords", "totalRecords", totalRecordCnt, fileId, map.toMap)
    }

    //kafkaMsg.sendRecordWithProperties(oxygenConf, metricTopic, new AcceptedRecordsLoggingEvent(fileId, acceptedRecordCnt).toString, fileId)
    //kafkaMsg.sendRecordWithProperties(oxygenConf, metricTopic, new RejectedRecordsLoggingEvent(fileId, rejectedRecordCnt).toString, fileId)

    val acceptedDFsWithContextualOverriddenEvents = contextualOverride(config, acceptedRecordsDS, fullPathFileName)

    if (acceptedRecordCnt > 0)
      moveAcceptedRecordsToFile(removeDeleteDirectiveEvents(config, acceptedRecordsDS), fileNameOnly, fileId, Option(processInstruction), comment)
    else {
      logger.error(s"Rejecting the file $fileNameOnly since all records were rejected by RecordLevelAcceptance though file level acceptance was successful.")
    }
    //If all records were rejected moveRejectedRecordsToFile will move marker to rejected, so indicate that condition in the call.
    if (rejectedRecordCnt > 0) moveRejectedRecordsToFile(rejectedRecordsDS, fileNameOnly, fileId, acceptedRecordCnt==0, processInstruction)
    if (!acceptedDFsWithContextualOverriddenEvents.isEmpty()) saveDeletedEvents(acceptedDFsWithContextualOverriddenEvents.toDS(), fileNameOnly, Option(processInstruction))
    if (acceptedRecordCnt == 0) true
    else moveValidatedFileToProcessed(fullPathFileName, fileNameOnly, getName(processInstruction.location())).get
  }

  private def removeDeleteDirectiveEvents(config: AcceptanceElement, acceptedRecordsDS: Dataset[TaggedValidatedEvent]) = {
    if (config.contextualOverride.isDefined) {
      val contextualOverride = config.contextualOverride.get
      val inferDeleteEnabled = contextualOverride.inferDelete.enabled
      val explicitDeleteEnabled = contextualOverride.explicitDelete.enabled

      val filteredEvents = if (inferDeleteEnabled || explicitDeleteEnabled) acceptedRecordsDS.filter(!TaggedValidatedEvent.isDeleteDirective(_))
      else acceptedRecordsDS
      filteredEvents
    }
    else acceptedRecordsDS
  }

  private def saveDeletedEvents(deletedEventsDS: Dataset[TaggedValidatedEvent], originFileName: String, processInstruction: Option[ProcessInstruction]): Unit = {
    val fileNameWithoutFileId = removeFileExtension(originFileName)
    val fileId = IdGenerator.getUniqueId(acceptanceTimestamp).toString
    val fileName = "inferred-" + fileNameWithoutFileId + "-asDeleted." + fileId
    moveAcceptedRecordsToFile(deletedEventsDS, fileName, fileId, processInstruction, "")
  }

  private[this] def readFile(fullPathFileName: String, config: AcceptanceElement): Dataset[TaggedValidatedEvent] = {
    if (readFormat == Constants.FILE_AVRO_FORMAT) {
      sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "true")
      sparkSession.read.format("avro").load(fullPathFileName).as[TaggedValidatedEvent]

    }
    else if (readFormat == Constants.FILE_PARQUET_FORMAT) {
      sparkSession.read.parquet(fullPathFileName).as[TaggedValidatedEvent]
    }
    else {
      sparkSession.read.json(fullPathFileName)
        .toJSON.rdd.map(y => JsonUtils.fromJson(y, classOf[TaggedValidatedEvent]))
        .toDS()
    }
  }

  private[this] def readFile(fullPathFileName: String): Dataset[TaggedValidatedEvent] = {
    if (readFormat == Constants.FILE_AVRO_FORMAT) {
      sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "true")
      sparkSession.read.format("avro").load(fullPathFileName).as[TaggedValidatedEvent]

    }
    else if (readFormat == Constants.FILE_PARQUET_FORMAT) {
      sparkSession.read.parquet(fullPathFileName).as[TaggedValidatedEvent]
    }
    else {
      sparkSession.read.json(fullPathFileName)
        .toJSON.rdd.map(y => JsonUtils.fromJson(y, classOf[TaggedValidatedEvent]))
        .toDS()
    }
  }

  private[this] def startProcessingFile(fullPathFileName: String, processInstruction: ProcessInstruction): Try[Boolean] = {
    Try({
      orchestrationHelper.markDatasetAsInProgress(processInstruction.getJobId, processInstruction.datasetId(), fullPathFileName)
    })
  }

  private[this] def moveValidatedFileToProcessed(fullPathFileName: String, fileNameOnly: String, fileId: String): Try[Boolean] = {
    Try({
      fileUtils.createDirectory(CompletedProcessingDir)
      val validatedProcessedFile = CompletedProcessingDir + fileNameOnly
      fileUtils.moveFile(fullPathFileName, validatedProcessedFile)
    })
  }

  private[this] def moveValidatedFileToProcessedWithStatus(processInstruction: ProcessInstruction, status: String, comments: String, detailedComments: String = ""): Try[Boolean] = {
    Try({
      val fullPathFileName: String = processInstruction.location()
      val fileNameOnly: String = getName(fullPathFileName)
      val fileId: String = processInstruction.datasetId()

      fileUtils.createDirectory(CompletedProcessingDir)
      val validatedProcessedFile = CompletedProcessingDir + fileNameOnly
      fileUtils.moveFile(fullPathFileName, validatedProcessedFile)
      orchestrationHelper.sendDatasetProcessUpdates(fileId, fileId, validatedProcessedFile, PmConstants.processType.END, status, Option(processInstruction.target()), comments, detailedComments)
    })
  }


//  private def moveFileToManualReview(processInstruction: String, fullPathFileName: String,
//                                     comment: String = "File acceptance and rejection expression evaluated to false"): Boolean = {
//    /*orchestrationHelper.markDatasetAsNoop(processInstruction.getJobId, processInstruction.datasetId(),
//      fullPathFileName, comment, target = Option(processInstruction.target()))*/
//
//  }

  private[this] def restoreInProcessFileToOriginal(processInstruction: ProcessInstruction, errorMessage: String): Try[Boolean] = {
    Try({
      moveValidatedFileToProcessedWithStatus(processInstruction, PmConstants.status.ERROR, errorMessage).get
    })
  }

  private[this] def moveFileToRejected(processInstruction: ProcessInstruction, errorMessage: String): Try[Boolean] = {
    Try({
      val fullPathFileName: String = processInstruction.location()
      val fileNameOnly: String = getName(fullPathFileName)
      val fileId = processInstruction.datasetId()
      val rejectedFileName = rejectedFilesOutputDir + removeExtension(fileNameOnly) + "." + fileId
      val retVal = fileUtils.moveFile(fullPathFileName, rejectedFileName)
      logger.debug(s"File rejected -- $fileNameOnly")
      orchestrationHelper.markDatasetAsFailed(processInstruction.getJobId, processInstruction.datasetId(), rejectedFileName, s"File Rejected. $errorMessage", errorMessage, target = Option(processInstruction.target))
      retVal
    })
  }

  private[this] def moveAcceptedRecordsToFile(acceptedRecordsDS: Dataset[TaggedValidatedEvent], fileNameOnly: String, fileId: String, processInstruction: Option[ProcessInstruction], acceptMessage: String): Try[Boolean] = {
    Try({
      logger.debug("In moveAcceptedRecordsToFile")
      val acceptedFileName = acceptedFilesOutputDir + fileNameOnly
      // TODO: test delete inference with this*/
      ioHelper.saveAccepteddData(acceptedRecordsDS, acceptedFileName, false)
     val processTarget = if (processInstruction.isDefined) Some(processInstruction.get.target()) else None
      if (acceptedFileName.matches(".*inferred-.*-asDeleted\\..*")) {
        processInstruction.get.setJobId(fileId)
        processInstruction.get.setDatasetId(fileId)
      }
      orchestrationHelper.markDatasetAsSuccess(processInstruction.get.getJobId, processInstruction.get.datasetId(), acceptedFileName, s"File Accepted. $acceptMessage", processTarget.get)
    })

  }

  private[this] def moveRejectedRecordsToFile(rejectedRecordsDS: Dataset[TaggedValidatedEvent], fileNameOnly: String, fileId: String, noRecAccepted: Boolean,
                                              processInstruction: ProcessInstruction): Try[Boolean] = {
    Try({
      logger.debug("In moveRejectedRecordsToFile")
      val rejectedFileName = rejectedRecordsOutputDir + removeExtension(fileNameOnly) + "." + fileId
      val ret = ioHelper.saveAccepteddData(rejectedRecordsDS, rejectedFileName, false)
      /*
       * Marker will get stuck at inProgress if an accepted file has none of its records accepted by recordAcceptance.
       * So under that condition move the marker to rejected and report the error message via process updates.
       */
      if (noRecAccepted) {
        val errorMessage = s"File Rejected. Reason: No records were accepted by record level acceptance check."
        logger.error(s"$rejectedFileName $errorMessage")
        orchestrationHelper.markDatasetAsFailed(processInstruction.getJobId, processInstruction.datasetId(), rejectedFileName,
          errorMessage, errorMessage, target = Option(processInstruction.target))
        fileUtils.moveFile(rejectedFileName, rejectedFileName)
      } else ret
    })
  }

  private def getAcceptAndRejectedRecords(validatedDS: Dataset[TaggedValidatedEvent]): (Dataset[TaggedValidatedEvent], Dataset[TaggedValidatedEvent]) = {
    val recordValidatedEventsWithAcceptanceFlag = validatedDS.map(taggedValidatedEvent => {
      val sourceFileName = taggedValidatedEvent.lineage.fileName
      val recordAcceptanceExpression = oxygenConf.oxygen.getAcceptanceInfo(sourceFileName).get.asInstanceOf[AcceptanceElement].recordAcceptance

      (taggedValidatedEvent, isRecordActionValid(taggedValidatedEvent, recordAcceptanceExpression))
    })

    val acceptedRecordsDS = recordValidatedEventsWithAcceptanceFlag
      .filter(event => {
        event._2
      })
      .map(event => event._1)

    val rejectedRecordsDS = recordValidatedEventsWithAcceptanceFlag
      .filter(event => {
        !event._2
      })
      .map(event => event._1)

    (acceptedRecordsDS, rejectedRecordsDS)
  }

  /**
   * Returns boolean after parsing the record acceptance/rejection expression
   *
   * @param record
   * @param recordExpression
   * @return
   */
  def isRecordActionValid(record: TaggedValidatedEvent, recordExpression: Option[String]): Boolean = {
    if (recordExpression.isDefined) {
      val result = Try({
        val recordParser = new RecordValidationParser(Left(record))
        recordParser.parseAll(recordParser.boolExpr, recordExpression.get).get
      }) match {
        case Success(res) => res
        case Failure(fRes) =>
          logger.error(s"Evaluation of expression ${recordExpression.get} failed. Reason: $fRes. Setting default evaluation to true.")
          true
      }
      result
    }
    else
      true
  }

  /**
   * Returns boolean after evaluating the file acceptance/rejection expression
   *
   * @param aggregatesParserDF Parser to evaluate the expression
   * @param fileExpression Expression to evaluate
   * @return Result of evaluating fileExpression if fileExpression is defined else false
   */
  def isFileActionValid(fileExpression: Option[String], aggregatesParserDF: ExternalActionParser): Boolean = {
    if (fileExpression.isDefined) {
      val res = Try(aggregatesParserDF.parseAll(aggregatesParserDF.boolExpr, fileExpression.get).get) match {
        case Success(sRes) =>
          sRes
        case Failure(fRes) =>
          logger.error(s"Evaluation of expression ${fileExpression.get} failed. Reason: $fRes. Setting default evaluation to false.")
          false
      }
      res
    }
    else
      false
  }

  /**
   * There are cases where a new incoming file contains events representing a new state that overrides the previous state.
   * In some such cases, an extension may want the framework to ignore previously ingested events.
   * Delete Inference is one such feature that allows an extension to configure Oxygen to ignore previously ingested events
   * when a new version of events are ingested.
   *
   * @param config
   * @param acceptedRecordsDF
   * @param fileName
   * @return
   */
  private[this] def contextualOverride(config: AcceptanceElement, acceptedRecordsDF: Dataset[TaggedValidatedEvent], fileName: String) = {
    try {
      if (config.contextualOverride.isDefined) {
        logger.info("--Contextual override config is defined")
        val deletedEvents = generateDeletedEvents(config, acceptedRecordsDF, fileName)
        deletedEvents
      }
      else
      // return the acceptedRecordsDF
        emptyRDDTaggedValidatedEvent
    }
    catch {
      case e: Throwable => {
        logger.error(e.getMessage)
        emptyRDDTaggedValidatedEvent
      }
    }
  }

  private[this] def convertAcceptedDFtoRDD(acceptedRecordsDF: DataFrame): RDD[TaggedValidatedEvent] = {
    val acceptedRDD = acceptedRecordsDF.toJSON.rdd
    val acceptedRDDEvents = acceptedRDD.mapPartitions(recIter => {
      recIter.map(r => {
        val taggedValidatedEvent: TaggedValidatedEvent = JsonUtils.fromJson(r, classOf[TaggedValidatedEvent])
        taggedValidatedEvent
      })
    })
    acceptedRDDEvents
  }

  /**
   *
   * @param config
   * @param acceptedRecordsDS
   * @param fileName
   * @return
   */
  private[this] def generateDeletedEvents(config: AcceptanceElement, acceptedRecordsDS: Dataset[TaggedValidatedEvent], fileName: String) = {
    val contextualOverride = config.contextualOverride.get
    val inferDeleteEnabled = contextualOverride.inferDelete.enabled
    val explicitDeleteEnabled = contextualOverride.explicitDelete.enabled

    if (explicitDeleteEnabled || inferDeleteEnabled) {
      // convert accepted events DataFrame to Tagged Validated Event RDD
      //      val acceptedRDDEvents = convertAcceptedDFtoRDD(acceptedRecordsDF)
      val acceptedRDDEvents = acceptedRecordsDS.rdd

      // get the current period from the file received date
      val anyOneAcceptedEvent = acceptedRDDEvents.first()
      val receivedDate = anyOneAcceptedEvent.lineage.receivedDate
      val subscriber = anyOneAcceptedEvent.lineage.subscriber
      val currentPeriod = Period.monthlyPeriodFromEpochDateTime(receivedDate)
      logger.debug(s"----Current Period: " + currentPeriod.id.toString)
      val contextualOverrider = new ContextualOverride(appContext, currentPeriod, subscriber)

      val inferredDeletedValidatedEvents = if (inferDeleteEnabled) {
        logger.info("--Delete Inference is enabled")
        val lookBackPeriod = contextualOverride.inferDelete.lookBackPeriod.getOrElse(1) // duration defaulted to lookup back one period
        val inferredDeletePredicatesList = contextualOverride.inferDelete.deletePredicates.getOrElse(List.empty)
        logger.debug(s"----Inferred Delete Look Back Period: " + lookBackPeriod.toString)
        logger.debug(s"----Inferred Delete Predicates: " + inferredDeletePredicatesList.toString())

        val inferredDeletedValidatedEventsRDDs = inferredDeletePredicatesList.map(deletePredicates =>
          contextualOverrider.inferDelete(lookBackPeriod, deletePredicates.toArray, acceptedRDDEvents, fileName, taggedCuratedEventsForContextual))
        val inferredDeletedValidatedEvents = appContext.sc.union(inferredDeletedValidatedEventsRDDs)

        logger.debug(taggedCuratedEventsForContextual.count().toString)
        logger.debug("----Count of delete inferred events: " + inferredDeletedValidatedEvents.count().toString)

        inferredDeletedValidatedEvents
      } else appContext.sc.emptyRDD[TaggedValidatedEvent]

      val explicitDeletedValidatedEvents = if (explicitDeleteEnabled) {
        val lookBackPeriodForExplicitDelete = contextualOverride.explicitDelete.lookBackPeriod.getOrElse(1)
        val explicitDeletePredicatesList = contextualOverride.explicitDelete.deletePredicates.getOrElse(List.empty)
        logger.debug(s"----Explicit Delete Look Back Period: " + lookBackPeriodForExplicitDelete.toString)
        logger.debug(s"----Explicit Delete Predicates: " + explicitDeletePredicatesList.toString())

        val explicitDeletedValidatedEventsRDDs = explicitDeletePredicatesList.map(deletePredicates =>
          contextualOverrider.explicitlyDelete(lookBackPeriodForExplicitDelete, deletePredicates.toArray, acceptedRDDEvents, fileName, taggedCuratedEventsForContextual))
        val explicitDeletedValidatedEvents = appContext.sc.union(explicitDeletedValidatedEventsRDDs)

        explicitDeletedValidatedEvents
      } else appContext.sc.emptyRDD[TaggedValidatedEvent]

      val deletedValidatedEvents = inferredDeletedValidatedEvents.union(explicitDeletedValidatedEvents)

      deletedValidatedEvents
    }
    else {
      // if inferDelete is not enabled return acceptedRecordsDF
      logger.info("--Delete Inference is not enabled")
      emptyRDDTaggedValidatedEvent
    }
  }

  private[acceptance] def readNonDeletedCuratedEvent: RDD[TaggedCuratedEvent] = {

    val pipelineConfiguration = oxygenConf.oxygen
    val storageFormat = pipelineConfiguration.FILE_STORAGE_FORMAT
    val readFormat = oxygenConf.oxygen.ACCEPTANCE_READ_WRITE_FORMAT.input.getOrElse(storageFormat)
    val compressionFormat = pipelineConfiguration.FILE_COMPRESSION_FORMAT

    logger.debug("----Reading Curated Events")
    val curatedRecords = if (readFormat == Constants.FILE_AVRO_FORMAT) {
      appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
      try {
        val curatedEventsDF = appContext.sparkSession.read.format("avro").load(currentNonDeletedCuratedDataset: _*)
        curatedEventsDF.toJSON.rdd
      }
      catch {
        case fne: FileNotFoundException => {
          appContext.sparkSession.emptyDataFrame.toJSON.rdd
        }
      }
    }
    else if (readFormat == Constants.FILE_PARQUET_FORMAT) {
      try {
        val curatedEventsDF = appContext.sparkSession.read.parquet(currentNonDeletedCuratedDataset: _*)
        curatedEventsDF.toJSON.rdd
      }
      catch {
        case fne: FileNotFoundException => {
          appContext.sparkSession.emptyDataFrame.toJSON.rdd
        }
      }
    }
    else {
      val curatedFiles = currentNonDeletedCuratedDataset
      if (curatedFiles == null || curatedFiles.isEmpty)
        appContext.sparkSession.emptyDataFrame.toJSON.rdd
      else {
        val curatedEventsDF = appContext.sparkSession.read.json(curatedFiles: _*)
        curatedEventsDF.toJSON.rdd
      }
    }

    curatedRecords.map(x => {
      JsonUtils.fromJson(x, classOf[TaggedCuratedEvent])
    })
  }

  private[acceptance] def currentNonDeletedCuratedDataset: List[String] = {
    val curationDir = oxygenConf.oxygen.stage.curation.get.paths.curatedDir
    val curatedFiles = fileUtils.listFilenames(curationDir, true).filter(x => !x.matches(".*inferred-.*-asDeleted\\..*"))
    curatedFiles
  }


  def emitStockMetric(name: String, key: String, value: Long, datasetId: String, extraFields : Map[String, Any] ): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricLong(
        name, MetricsConstants.Actions.ACCEPTANCE, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: Double, datasetId: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricDouble(
        name, MetricsConstants.Actions.ACCEPTANCE, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  def emitStockMetric(name: String, key: String, value: String, datasetId: String, extraFields : Map[String, Any]): Any = {
    metricsEmitter.emitCritical(
      MetricEventsFactory.generationDataMetrics.htStageMetricString(
        name, MetricsConstants.Actions.ACCEPTANCE, datasetId, key, value, OxygenCollectionsUtils.asJavaMapObject(extraFields)))
  }

  private[acceptance] def buildReasonCodeSeverityMap(lookupMappingOpt: Option[LookupMapping], conditionsMap: Map[String, AcceptanceCondition]): Map[String, Int] = {
    if (lookupMappingOpt.isEmpty) {
      logger.info(s"No lookupMapping defined.")
      return Map.empty[String, Int]
    }
    val lookupMapping = lookupMappingOpt.get
    if (lookupMapping.tableName.isEmpty || lookupMapping.mapFromColumn.isEmpty || lookupMapping.mapToColumn.isEmpty) {
      logger.info(s"Invalid lookupMapping config. $lookupMapping")
      return Map.empty[String, Int]
    }
    val tableName = lookupMapping.tableName.get
    val keyName = lookupMapping.mapFromColumn.get
    val valueName = lookupMapping.mapToColumn.get
    val sevMap = scala.collection.mutable.Map.empty[String, Int]
    for (elem <- conditionsMap.values) {
      if (!elem.reasonCode.isEmpty) {
        val rowsTry = Try(OxygenLookupService.getService.get(tableName, Map(keyName -> elem.reasonCode)))
        rowsTry match {
          case Success(rows) => if (rows.isDefined) {
            val c = rows.get (0).get (valueName)
            if (c.isDefined) sevMap += (elem.reasonCode -> c.get.toString.toInt)
          }
          case Failure(exception) => logger.error(s"Error occurred in lookup for Table: $tableName" +
            s" Column: $keyName Value: ${elem.reasonCode}. Reason ${exception.getMessage}")
        }
      }
    }
    sevMap.toMap
  }

  private def getFile(folderPath: String):List[(String,String)] = {
    // This filters out .DS_Store files, as they are automatically created by Mac computers,
    val filterFiles = fileUtils.getNewFilesToProcess(List(folderPath)).filter{ case (filename, _)=>
      !filename.startsWith(".DS")
    }

    if(!filterFiles.isEmpty){
      logger.info("Getting file Information")
      filterFiles
    }else{
      logger.info("File doesn't exists or is empty")
      List.empty[(String,String)]
    }


  }

  def removeDefaultOutputStructure(): Unit = {
    val accpetanceDir = oxygenConf.oxygen.ACCEPTED_DIRECTORY
    val userHomeDir = oxygenConf.configMap.getOrElse("oxygen.application.paths.userHome", "")

    val defaultDirExists = userHomeDir != "" & !accpetanceDir.contains(userHomeDir) &  fileUtils.exists(userHomeDir)

        if (defaultDirExists) {
          fileUtils.deleteFileOrFolder(userHomeDir)
          logger.info("Default output structure removed.")
        }
      }
}