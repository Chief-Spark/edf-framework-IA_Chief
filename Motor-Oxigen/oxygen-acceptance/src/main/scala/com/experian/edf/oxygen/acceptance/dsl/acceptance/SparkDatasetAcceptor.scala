package com.experian.edf.oxygen.acceptance.dsl.acceptance

import com.experian.edf.oxygen.acceptance.dsl.acceptance.metrics.AcceptanceMetrics
import com.experian.edf.oxygen.acceptance.engines.dsl.{AcceptanceEvaluator, AcceptanceStatement}
import com.experian.edf.oxygen.acceptance.engines.io.OxygenIOUtils
import com.experian.edf.oxygen.acceptance.ingestion.config.DatasetAcceptanceConfig
import com.experian.edf.oxygen.acceptance.util.dsl.ValidatedEventParser
import com.experian.edf.oxygen.acceptance.util.{AggregatesParserDF, ExternalActionParser}
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.AcceptanceCondition
import com.experian.edf.oxygen.domain.internal.events.ValidatedEvent
//import com.experian.edf.oxygen.context.SparkOxygenAppContext
//import com.experian.edf.oxygen.domain.dsl.events.ValidatedEvent
import com.experian.edf.oxygen.utils.FileUtils
import com.experian.edf.oxygen.global.processmngt.ProcessInstruction
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.orchestration.OrchestrationHelper
import org.apache.commons.io.FilenameUtils.{getBaseName, getName, removeExtension}
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.{DataFrame, Dataset}
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import scala.collection.mutable
import scala.util.{Failure, Success, Try}
import com.experian.edf.oxygen.acceptance.engines.context.SparkOxygenAppContext


class SparkDatasetAcceptor(appContext: SparkOxygenAppContext, datasetAcceptanceConfig: DatasetAcceptanceConfig, orchestrationHelper: OrchestrationHelper, lookupBC: Broadcast[OxygenLookup], fileUtils: FileUtils) extends Serializable {
  @transient lazy val logger: Logger = getLogger(this.getClass)

  val conditionsMap: Map[String, AcceptanceCondition] = getConditions
  val severityCodes: Map[String, Int] = getReasonCodeSeverityMap

  private val ioUtils: OxygenIOUtils = OxygenIOUtils(spark = appContext.sparkSession)

  /**
   * Validated if filename contains automatic rejection suffix
   *
   * @param processInstruction File information that will be checked
   * @return If the file was automatically rejected or not
   */
  private[acceptance] def shouldAutomaticReject(processInstruction: ProcessInstruction): Boolean = {
    val fileNameOnly: String = getBaseName(processInstruction.getLocation)
    val shouldReject = (fileNameOnly.contains(Constants.ERROR_IN_HEADER) ||
      fileNameOnly.contains(Constants.ERROR_IN_FOOTER) ||
      fileNameOnly.contains(Constants.ERROR_IN_FILENAME) ||
      fileNameOnly.contains(Constants.ERROR_IN_HISTORIC_ATTR) ||
      fileNameOnly.contains(Constants.ERROR_IN_EMPTY_FILE_BODY)
    )
    if (shouldReject) moveFileToRejected(processInstruction,"Automatic rejected")
    shouldReject
  }
  /**
   * Handle expressions for all the action types (Reject, Accept, Manual Reject, Manual Accept and Manual Review)
   *
   * @param evaluator Evaluator that will be used to parse the action expressions
   * @param externalActionParser Parser used to evaluate the file conditions
   * @param processInstruction File information that will be processed
   * @param action REJECT, ACCEPT, MANUAL_REJECT, MANUAL_ACCEPT or MANUAL_REVIEW
   * @return Try[(Boolean,String)] If action evaluation was successful and action message with reason codes
   */
  private[this] def handleExpression(evaluator: AcceptanceEvaluator, externalActionParser: ExternalActionParser, processInstruction: ProcessInstruction, action: String): Try[(Boolean,String)] = {
    Try({
      val fileId: String = processInstruction.datasetId()
      val actionExpr: String = action match {
        case "REJECT" => Option(datasetAcceptanceConfig.fileActions.reject).filterNot(_.isEmpty).getOrElse("false")
        case "ACCEPT" => Option(datasetAcceptanceConfig.fileActions.accept).filterNot(_.isEmpty).getOrElse("true")
        case "MANUAL_REJECT" => datasetAcceptanceConfig.fileActions.manualReject
        case "MANUAL_ACCEPT" => datasetAcceptanceConfig.fileActions.manualAccept
        case "MANUAL_REVIEW" => datasetAcceptanceConfig.fileActions.manualReview
        case _ => null
      }
      if (actionExpr == null || actionExpr.trim.isEmpty) {
        logger.info(s"Skipping $action check for dataset file id $fileId and returning false since it is not configured.")
        return Success(false, "")
      }
      //Evaluate using new Acceptance DSL syntax with default evaluation as false.
      val exec = evaluator.compile(actionExpr)

      val (actionResult, resultLog) = if (exec.isDefined) {
        evalExpressionWithLog(evaluator, externalActionParser, exec)
      } else {
        logger.info(s"Evaluating manual $action configuration failed with new syntax. Please check syntax. manual$action evaluation result set to false.")
        (false, "")
      }
      val actionMsg = s"Action $action expression ${actionExpr} evaluated to: $actionResult. Reason Codes: ${resultLog}."
      logger.info(actionMsg)
      return Success(actionResult, actionMsg)
    })
  }

  private[acceptance] def moveFileToRejected(processInstruction: ProcessInstruction, errorMessage: String): Try[Boolean] = {
    Try({
      val fullPathFileName: String = processInstruction.location()
      val fileNameOnly: String = getName(fullPathFileName)
      val fileId: String = processInstruction.datasetId()
      val rejectedFilesOutputDir = appContext.config.oxygen.REJECTED_FILES_DIRECTORY + File.separator // Rejected files DIR
      val rejectedFileName = rejectedFilesOutputDir + removeExtension(fileNameOnly) + "." + fileId
      val retVal = fileUtils.moveFile(fullPathFileName, rejectedFileName)
      logger.debug(s"File rejected -- $fileNameOnly")
      orchestrationHelper.markDatasetAsFailed(processInstruction.getJobId, processInstruction.datasetId(), rejectedFileName, s"File Rejected. $errorMessage", errorMessage, target = Option(processInstruction.target))
      retVal
    })
  }

  private[this] def moveValidatedFileToProcessed(processInstruction: ProcessInstruction): Try[Boolean] = {
    Try({
      val processedDir = appContext.config.oxygen.PROCESSED_VALIDATED_DIRECTORY + File.separator
      val processedFilePath = processedDir + getName(processInstruction.getLocation)
      fileUtils.createDirectory(processedDir)
      fileUtils.moveFile(processInstruction.getLocation, processedFilePath)
    })
  }

  private[this] def moveFileToNoop(processInstruction: ProcessInstruction,
                                     comment: String): Boolean = {
    val fullPathFileName: String = processInstruction.location()
    orchestrationHelper.markDatasetAsNoop(processInstruction.getJobId, processInstruction.datasetId(),
      fullPathFileName, comment, target = Option(processInstruction.target()))
  }
  private[this] def moveRecordsToFile(acceptedRecordsDS: Dataset[ValidatedEvent], fileName:String): Try[Boolean] = {
    Try({
      ioUtils.write.dataSet(acceptedRecordsDS,fileName,"text")
    })
  }

  /**
   * Returns boolean after parsing the record acceptance/rejection expression
   *
   * @param record
   * @param recordExpression
   * @return
   */
    private[this] def isRecordActionValid(record: ValidatedEvent, recordExpression: String): Boolean = {
    if (recordExpression != null && recordExpression.nonEmpty) {
      val result = Try({
        val recordParser = new ValidatedEventParser(record)
        recordParser.parseAll(recordParser.boolExpr, recordExpression).get
      }) match {
        case Success(res) => res
        case Failure(fRes) =>
          logger.error(s"Evaluation of expression ${recordExpression} failed. Reason: $fRes. Setting default evaluation to true.")
          true
      }
      result
    }
    else {
      logger.warn(s"Record evaluation expression empty. Setting default evaluation to true.")
      true
    }
  }

  /**
   * Divide records in two datasets: accepted and rejected records
   *
   * @param validatedDS Validated records
   * @return (acceptedRecords, rejectedRecords)
   */
  private[this] def getAcceptedAndRejectedRecords(validatedDS: Dataset[ValidatedEvent]): (Dataset[ValidatedEvent], Dataset[ValidatedEvent]) = {
    import appContext.sparkSession.implicits._
    val recordValidatedEventsWithAcceptanceFlag = validatedDS.map(validatedEvent => {
      (validatedEvent, isRecordActionValid(validatedEvent, datasetAcceptanceConfig.recordAcceptance))
    }).cache()

    val acceptedRecordsDS = recordValidatedEventsWithAcceptanceFlag
      .filter(event => {event._2})
      .map(event => event._1)

    val rejectedRecordsDS = recordValidatedEventsWithAcceptanceFlag
      .filter(event => {!event._2})
      .map(event => event._1)

    (acceptedRecordsDS, rejectedRecordsDS)
  }

  private[this] def handleRecordLevelAcceptance(processInstruction: ProcessInstruction, validatedEvents: Dataset[ValidatedEvent], comment : String): Boolean = {
    val fullPathFileName: String = processInstruction.location()
    val fileNameOnly: String = getName(fullPathFileName)
    val fileId: String = processInstruction.datasetId()
    // Split the file into accepted / rejected records
    val (acceptedRecordsDS: Dataset[ValidatedEvent], rejectedRecordsDS: Dataset[ValidatedEvent]) = getAcceptedAndRejectedRecords(validatedEvents)

    val acceptedRecordCnt = acceptedRecordsDS.count
    val rejectedRecordCnt = rejectedRecordsDS.count
    val totalRecordCnt = acceptedRecordCnt + rejectedRecordCnt
    logger.info(s"Record level acceptance of $fileNameOnly acceptedRecordCnt = $acceptedRecordCnt rejectedRecordCnt = $rejectedRecordCnt")

    val map = mutable.HashMap[String, Any]()
    AcceptanceMetrics.emitStockMetric("acceptedRecords","acceptedRecords", acceptedRecordCnt, fileId, map.toMap)
    AcceptanceMetrics.emitStockMetric("rejectedRecords","rejectedRecords", rejectedRecordCnt, fileId, map.toMap)
    AcceptanceMetrics.emitStockMetric("totalRecords","totalRecords", totalRecordCnt, fileId, map.toMap)
    val acceptedFileName = appContext.config.oxygen.ACCEPTED_DIRECTORY + File.separator + fileNameOnly
    val rejectedFileName = appContext.config.oxygen.REJECTED_RECORDS_DIRECTORY + File.separator + fileNameOnly

    if (acceptedRecordCnt > 0) {
      moveRecordsToFile(acceptedRecordsDS, acceptedFileName)
    } else
      logger.error(s"Rejecting the file $fileNameOnly since all records were rejected by RecordLevelAcceptance though file level acceptance was successful.")
    //If all records were rejected moveRejectedRecordsToFile will move marker to rejected, so indicate that condition in the call.
    if (rejectedRecordCnt > 0) {
      moveRecordsToFile(rejectedRecordsDS, rejectedFileName)
    }
    if (acceptedRecordCnt == 0){
      val errorMessage = s"File Rejected. Reason: No records were accepted by record level acceptance check."
      logger.error(s"$rejectedFileName $errorMessage")
      orchestrationHelper.markDatasetAsFailed(processInstruction.getJobId, processInstruction.datasetId(), rejectedFileName,
        errorMessage, errorMessage, target = Option(processInstruction.target))
    } else {
      orchestrationHelper.markDatasetAsSuccess(processInstruction.getJobId, processInstruction.datasetId(), acceptedFileName,
        s"File Accepted. $comment", target = Option(processInstruction.target))
    }
    moveValidatedFileToProcessed(processInstruction).get
  }

  private[acceptance] def processFile(processInstruction: ProcessInstruction): Boolean = {
    val fileId: String = processInstruction.datasetId()
    val evaluator = new AcceptanceEvaluator
    evaluator.apply(conditionsMap, severityCodes)
    // Read the validated files as dataframe. Cache it.
    val validatedEvents: Dataset[ValidatedEvent] = ioUtils.read.validatedEvents(List(processInstruction.getLocation), "text").cache

    //Manual Reject should only evaluate External Action so not getting stats from validated data
    val emptyDF = appContext.sparkSession.emptyDataFrame
    val externalActionParserManualReject: ExternalActionParser = new ExternalActionParser(appContext.config, appContext.sparkSession.sqlContext, emptyDF, fileId)
    handleExpression(evaluator, externalActionParserManualReject, processInstruction, "MANUAL_REJECT") match {
      case Success((true, resultMessage)) =>
        logger.info("Manual reject expression evaluated to true")
        moveFileToRejected(processInstruction, resultMessage)
        return false
      case _ =>
    }

    val qsStatsDF: DataFrame = AggregatesParserDF.createQSDataFrameVE(appContext.sparkSession, validatedEvents).cache()
    val externalActionParserManualAccept: ExternalActionParser = new ExternalActionParser(appContext.config, appContext.sparkSession.sqlContext, qsStatsDF, fileId)
    handleExpression(evaluator, externalActionParserManualAccept, processInstruction, "MANUAL_ACCEPT") match {
      case Success((true, resultMessage)) =>
        logger.info("Manual accept expression evaluated to true")
        qsStatsDF.unpersist(false)
        handleRecordLevelAcceptance(processInstruction,validatedEvents,resultMessage)
        return true
      case _ =>
    }

    handleExpression(evaluator, externalActionParserManualAccept, processInstruction, "REJECT") match {
      case Success((true, resultMessage)) =>
        logger.info("Reject expression evaluated to true")
        qsStatsDF.unpersist(false)
        moveFileToRejected(processInstruction, resultMessage)
        return false
      case _ =>
    }

    handleExpression(evaluator, externalActionParserManualAccept, processInstruction, "MANUAL_REVIEW") match {
      case Success((true, resultMessage)) =>
        logger.info("Manual Review expression evaluated to true")
        qsStatsDF.unpersist(false)
        moveFileToNoop(processInstruction,"Manual Review expression evaluated to true")
        return true
      case _ =>
    }

    handleExpression(evaluator, externalActionParserManualAccept, processInstruction, "ACCEPT") match {
      case Success((true, resultMessage)) =>
        logger.info("Accept expression evaluated to true")
        qsStatsDF.unpersist(false)
        handleRecordLevelAcceptance(processInstruction,validatedEvents,resultMessage)
        return true
      case _ =>
    }
    moveFileToNoop(processInstruction,"File acceptance and rejection expressions evaluated to false")
    false
  }

  private[this] def evalExpressionWithLog(evaluator: AcceptanceEvaluator, aggregatesParserDF: ExternalActionParser, exec: Option[AcceptanceStatement]): (Boolean, String) = {
    val resultLog: StringBuilder = StringBuilder.newBuilder
    val (shouldRejectLog, reasons) = evaluator.execute(exec.get, conditionsMap.values.toList, aggregatesParserDF)
    val log = reasons.filter(_.isLogged).map(reason => s"${reason.reasonCode}: ${reason.result}")
    resultLog.append(log.mkString(" "))
    (shouldRejectLog, resultLog.result())
  }

  private[this] def getConditions: Map[String, AcceptanceCondition] = {
    datasetAcceptanceConfig.fileConditions.map(fileCondition => fileCondition.name -> AcceptanceCondition(fileCondition.name,fileCondition.expression,fileCondition.reasonCode,fileCondition.isLogged,fileCondition.result)).toMap
  }
  private[this] def getReasonCodeSeverityMap: Map[String, Int] = {
    if (datasetAcceptanceConfig.lookupMapping.isEmpty) {
      logger.info(s"No lookupMapping defined.")
      return Map.empty[String, Int]
    }
    val lookupMapping = datasetAcceptanceConfig.lookupMapping.get
    if (lookupMapping.tableName.isEmpty || lookupMapping.codeColumn.isEmpty || lookupMapping.severityColumn.isEmpty) {
      logger.info(s"Invalid lookupMapping config. $lookupMapping")
      return Map.empty[String, Int]
    }
    val tableName = lookupMapping.tableName
    val keyName = lookupMapping.codeColumn
    val valueName = lookupMapping.severityColumn
    val sevMap = scala.collection.mutable.Map.empty[String, Int]
    for (elem <- conditionsMap.values) {
      if (elem.reasonCode.nonEmpty) {
        val rowsTry = Try(lookupBC.value.get(tableName, Map(keyName -> elem.reasonCode)))
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
}
