package com.experian.edf.oxygen.validation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.{Metrics, Validate, ValidationElement}
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.internal.{Frequency, Lineage, MetricData, Payload}
import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.processors.ProcessorContext
import com.experian.edf.oxygen.domain.validation.FileValidationResult
import com.experian.edf.oxygen.domain.{TaggedValidatedEvent, processors}
import com.experian.edf.oxygen.global.processmngt.ProcessInstruction
import com.experian.edf.oxygen.global.utils.{HashFunctions, IdGenerator, ResourceUtils}
import com.experian.edf.oxygen.logging.{DatasetProcessLoggingHelper, MetricsEvent}
import com.experian.edf.oxygen.lookup.OxygenLookupService
import com.experian.edf.oxygen.module.logging.metrics.MetricsConstants
import com.experian.edf.oxygen.orchestration.OrchestrationContext
import com.experian.edf.oxygen.processmngt.ProcessManagementClient
import com.experian.edf.oxygen.processmngt.file.KafkaMsg
import com.experian.edf.oxygen.validation.context.{ContextualValidatorWithCuratedEvents, ContextualValidatorWithPinnedEventsV1, ContextualValidatorWithPinnedEventsV2, ContextuallyValidateSubscriberRelation, ContextuallyValidateWithExtractor, ValidationContext}
import com.experian.edf.oxygen.validation.domain.ApplicationRunMode.BATCH
import com.experian.edf.oxygen.validation.log.{FileMetadataLoggingEvent, FileProcessorLoggingEvent, FilenameValidationEvent, FooterValidationEvent, HeaderValidationEvent, HistoricAttributesValidationEvent, MetricNameExprEvent, TotalRecordsMetrics, ValidationDatasetMetricsLoggingEvent, ValidationFieldMetricEvent, ValidationMetricsLoggingEvent}
import com.experian.edf.oxygen.validation.domain.ValidationProcessor
import com.experian.edf.oxygen.validation.engines.SparkValidator.readTextFileWithCharset
import com.experian.edf.oxygen.validation.utils.{AggregatesParserDS, RecordValidationParser, ValidationIOUtils}
import com.experian.edf.oxygen.validation.validator.{ValidationData, ValidationError, ValidationResult}
import com.experian.edf.oxygen.validation.xml.{OxygenXmlStreamReader, XMLValidator, XsdValidationResult}
import com.experian.edf.oxygen.validator.internal.ObjectError
import com.google.gson.{JsonArray, JsonParser}
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringEscapeUtils.escapeJava
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapred
import org.apache.hadoop.mapred.TextInputFormat
import org.apache.spark.TaskContext
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Column, DataFrame, Dataset}
import org.apache.spark.sql.functions.{col, explode}
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory.getLogger

import java.io.File
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.HashMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class SparkValidatorCustomMode(appContext: SparkOxygenAppContext) extends Validator(appContext) {

  import sparkSession.implicits._

  override def createOrchestrationContext: OrchestrationContext = OrchestrationContext(
    action = MetricsConstants.Actions.VALIDATION,
    source = MetricsConstants.Sources.VALIDATOR,
    orchestrationSource = oxygenConf.oxygen.VALIDATION_ORCHESTRATION.source,
    orchestrationTarget = Option(List(oxygenConf.oxygen.VALIDATED_DIRECTORY)),
    config = oxygenConf,
    applicationId = Option(appContext.sc.applicationId))

  val config = appContext.config
  val validationTimestamp = System.currentTimeMillis() // Generate and use the timestamp for the whole validation stage run
  val runID = IdGenerator.getUniqueId(validationTimestamp).toString
  val CompletedProcessingDir = oxygenConf.oxygen.PROCESSED_DATAHOME_DIRECTORY + File.separator
  val sparkSession = appContext.sparkSession // Spark session
  val emptyDataFrame = appContext.sparkSession.sqlContext.emptyDataFrame

  // TODO: Change the validationValues call so  that it returns a ValidationElement, not ProcessElement.
  val validationConfig = oxygenConf.oxygen.validationValues // Validation config section from config file
  val storageFormat = oxygenConf.oxygen.FILE_STORAGE_FORMAT // JSON, Avro, Parquet???
  val writeFormat = oxygenConf.oxygen.VALIDATION_READ_WRITE_FORMAT.output.getOrElse(storageFormat)
  val validationOutputDir = oxygenConf.oxygen.VALIDATED_DIRECTORY + File.separator // Validation output DIR
  val validationInputDir = oxygenConf.oxygen.VALIDATION_DIRECTORY + File.separator //Validation input DIR
  val emptyTaggedValidaedEvent = appContext.sparkSession.emptyDataset[TaggedValidatedEvent]
  val runDuration = oxygenConf.oxygen.RUN_DURATION.getOrElse(-1L)
  val edgenodeHostname = java.net.InetAddress.getLocalHost.getHostName
  val pid = ResourceUtils.getProcessID
  // kafka properties
  val metricTopic = config.oxygen.getKafkaLoggerTopic.get("metricTopic").getOrElse("")
  val puTopic = config.oxygen.getKafkaLoggerTopic.get("puTopic").getOrElse("")

  private val emitStockMetricsEnabled = oxygenConf.configMap.get("oxygen.features.enable.emitStockMetrics").get.toBoolean
  println(s"emitStockMetrics = ${emitStockMetricsEnabled.toString}")

  val KafkaMsg = new KafkaMsg(oxygenConf)

  private val ioHelper = new ValidationIOUtils(appContext, fileUtils)

  object InvalidType extends Enumeration {
    type Type = Value
    val Header, Footer, EmptyFile, HistoricAttributes, Filename, StageQualityNotMet = Value
  }

  override def stop: Any = {
    super.stop
    appContext.sc.stop
  }

  @throws(classOf[OxygenException])
  override def run(incremental: Boolean): Any = {

    startService()

    val datasetToValidate = fileUtils.getNewFilesToProcess(List(validationInputDir))
    val sparkCustomValidation = new SparkValidatorCustomMode(appContext)
    datasetToValidate.foreach(file => {
      val processInstruction = new ProcessInstruction()
      processInstruction.setStage("Validation")
      processInstruction.setJobId(String.valueOf(pid))
      processInstruction.setTarget(validationOutputDir)
      processInstruction.setLocation(file._1)
      processInstruction.datasetId(runID)
      sparkCustomValidation.customValidationExecution(Option(processInstruction))
    })

    val userHomeDir = oxygenConf.configMap.getOrElse("oxygen.application.paths.appHome", "")

    if (userHomeDir != "" &
      ((!validationOutputDir.contains(userHomeDir)) | (!validationInputDir.contains(userHomeDir))) &
      fileUtils.exists(userHomeDir)) {
        fileUtils.deleteFileOrFolder(userHomeDir)
        logger.info("Default output structure removed.")
    }

    endService()

  }

  private[oxygen] def customValidationExecution(retrieveDataset: Option[ProcessInstruction]) = {
    val fileToValidate = retrieveDataset.get
    logger.debug("Next file to validate is...:" + fileToValidate)

    // check if file path is valid and exists
    try {
      // validation in progress - update
      //sendDatasetProcessUpdate(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), PmConstants.processType.START, PmConstants.status.IN_PROGRESS, "Validation in progress")
      //orchestrationHelper.markDatasetAsInProgress(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location())
      logger.info("Storage Format : " + writeFormat)


      // If there are no files to process, then log the message and move forward.
      val fullPathFileName = fileToValidate.location() //Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fileToValidate.location())
      val fileId = fileToValidate.datasetId()

      // Rename file to .inprocess and start processing. If another validator job has already picked it up, move to next file in list
      startProcessingFile(fileToValidate) match {
        case Success(true) => {
          val validationElement = Try(validationConfig.find(elem => fullPathFileName matches (elem._1)).get._2.asInstanceOf[ValidationElement])
          validationElement match {
            case Success(config) => {
              processFile(fileToValidate, config) match {
                case Success(true) => {
                  successfullyCompleteProcessingFile(fileToValidate)
                }
                case Failure(ex) => logger.error(s"Exception ${ex.getMessage}. Error in processing file ${fullPathFileName}. Aborting...", ex)
                case _ => logger.error(s"Error in processing file ${fullPathFileName}. Aborting...")
              }

            }
            case Failure(ex) => {
              logger.error(s"No validation configuration found for $fullPathFileName")
              restoreInProcessFileToOriginal(fileToValidate)
            }
          }

        } // End ValidationElement match Success(true)
      } // End startProcessingFile


      // update status of the validation
      // retrieve next file

    }
    catch {
      case faee: FileAlreadyExistsException => {
        //sendDatasetProcessUpdate(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), PmConstants.processType.END, PmConstants.status.ERROR, faee.getMessage + ". Please clean up directory before continuing. ")
        orchestrationHelper.markDatasetAsError(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), "Validation Error", faee.getMessage + ". Please clean up directory before continuing. ", target = Option(fileToValidate.target))
        orchestrator.close()
        logger.error(faee.getMessage, new OxygenException(faee.getMessage + ". Please clean up directory before continuing"))
      }
      case hfaee: mapred.FileAlreadyExistsException => {
        //sendDatasetProcessUpdate(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), PmConstants.processType.END, PmConstants.status.ERROR, hfaee.getMessage + ". Please clean up directory before continuing. ")
        orchestrationHelper.markDatasetAsError(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), "Validation Error", hfaee.getMessage + ". Please clean up directory before continuing. ", target = Option(fileToValidate.target))
        orchestrator.close()
        logger.error(hfaee.getMessage, new OxygenException(hfaee.getMessage + ". Please clean up directory before continuing"))
      }
      case ex: Exception => {
        //sendDatasetProcessUpdate(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), PmConstants.processType.END, PmConstants.status.ERROR, ex.getMessage)
        orchestrationHelper.markDatasetAsError(fileToValidate.jobId(), fileToValidate.datasetId(), fileToValidate.location(), "Validation Error", ex.getMessage + ". Please clean up directory before continuing. ", target = Option(fileToValidate.target))
        orchestrator.close()
        logger.error(ex.getMessage, ex)
      }
    }
  }

  private[this] def isResultValid(validationValue: ValidationResult, expression: Option[String]): Boolean = {

    // If Validation has no errors or validation is ValidationSuccess the result is valid
    // Else, validate using the parser. If there is no expression defined for the file,
    // result is to be manually accepted / rejected (no automatic rejection). Errors / Failures can be logged.
    // If there is an expression, look for the errorCode
    if (validationValue.successful || (expression == None))
      true
    else {
      // TODO: WIP. Leaving half done, so that pinning framework can be integrated.
      val parser = RecordValidationParser(Right(validationValue))
      val parseResult = parser.parseAll(parser.boolExpr, expression.get).get
      parseResult
    }
  }

  private[this] def successfullyCompleteProcessingFile(processInstruction: ProcessInstruction): Try[Boolean] = {

    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fullPathFileName)
      val fileId = processInstruction.datasetId()
      val processedFile = CompletedProcessingDir + fileNameOnly + "." + fileId
      val retVal = fileUtils.moveFile(fullPathFileName, processedFile)
      logger.info("Completed Processing file " + fullPathFileName)
      val processUpdate = DatasetProcessLoggingHelper.updateDataHomeProcessing(processInstruction.datasetId(), fileNameOnly + "." + fileId)
      KafkaMsg.sendRecordWithProperties(oxygenConf, puTopic, processUpdate, fileId)
      retVal
    })
  }


  private[this] def failedProcessingFile(processInstruction: ProcessInstruction, suffix: String): Try[Boolean] = {

    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fullPathFileName)
      val fileId = processInstruction.datasetId()

      orchestrationHelper.markDatasetAsFailed(processInstruction.getJobId, processInstruction.datasetId(), fullPathFileName, s"Validation Failed due to $suffix", target = Option(processInstruction.target))

    })
  }

  private[this] def startProcessingFile(processInstruction: ProcessInstruction): Try[Boolean] = {

    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fullPathFileName)
      val fileId = processInstruction.datasetId()

      logger.info("Start Processing file " + fullPathFileName)
      orchestrationHelper.markDatasetAsInProgress(processInstruction.getJobId, processInstruction.datasetId(), fullPathFileName)
    })
  }

  private[this] def restoreInProcessFileToOriginal(processInstruction: ProcessInstruction): Try[Boolean] = {
    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fullPathFileName)
      val fileId = processInstruction.datasetId()
      val retVal = orchestrationHelper.markDatasetAsError(processInstruction.getJobId, processInstruction.datasetId(), fullPathFileName, "No Processor found", target = Option(processInstruction.target))
      retVal
      // TODO: Send process update for "Finished validation on file with error"
    })
  }

  /**
   * To be invoked when the validation fails for header, footer and filename but the file has to be moved to validated directory
   * which in turn can be rejected during the acceptance phase
   */
  private[this] def validationErrorFile(config: ValidationElement, processor: Broadcast[ValidationProcessor], processInstruction: ProcessInstruction,
                                        suffix: String, validationResult: ValidationResult, eventType: Long, errorMessage: Option[String]): Try[Boolean] = {
    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fullPathFileName)
      val fileId = processInstruction.datasetId()
      // create a taggedValidatedEvent
      val lineage = processor.value.eventLineage
      val eventId = IdGenerator.nextEventId(appContext.config.oxygen.JOB_ID, TaskContext.getPartitionId())
      val newEvent = new TaggedValidatedEvent(lineage, eventId, null, null.asInstanceOf[Payload],
        Array.empty[QualityScore], validationResult.errors, eventType, null.asInstanceOf[Frequency], lineage.receivedDate, false, "", lineage.subscriber, Array.empty[ObjectError], Array.empty[ObjectError])
      //to DataSet[TaggedValidatedEvent]
      import appContext.sparkSession.implicits._
      val eventAsDs = appContext.sc.parallelize(Seq(newEvent)).toDS()

      val fileWithSuffix = fileNameOnly + "." + suffix

      // save file in validated folder with that event : saveValidatedEvents
      //val createDf = createValidatedEventsDf(eventAsDs, config)
      saveValidatedEvents(eventAsDs, processInstruction, fileWithSuffix, errorMessage).get
    })

  }

  private[this] def processFile(processInstruction: ProcessInstruction, config: ValidationElement): Try[Boolean] = {
    // TODO: ??: try block
    // Read in the text file as a data frame
    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileId = processInstruction.datasetId()

    val (headers, recs, footers, results) = readFile(fullPathFileName, config.encoding, config, fileId)

    // Find the processor for the class. If not found, log error and continue
    retrieveProcessorForFile(config: ValidationElement, processInstruction, headers, footers, results) match {
      case Success(processor) => {
        KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, FileMetadataLoggingEvent(fileId, processor.value.fileMetadata).toString, fileId)
        runBasicValidationAndThenProceed(processInstruction, config, headers, recs, footers, processor)
      }
      case Failure(error) => {
        recs.unpersist(false) // appContext.sc.textFile(fullPathFileName).toDS().cache()
        val fileProcessorLoggingEvent = new FileProcessorLoggingEvent(fileId,"manual review")
        KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, fileProcessorLoggingEvent.toString, fileId)
        errorRetrieveProcessorForFile(error, processInstruction)
      }
    }
  }

  private[this] def errorRetrieveProcessorForFile(error: Throwable, processInstruction: ProcessInstruction): Try[Boolean] = {
    // Rename file to original and leave as unprocessed. Write error log to
    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name

      logger.error(s"Error retrieving or instantiating processor for $fullPathFileName. Error is $error -> ${error.getMessage}")
      if (logger.isDebugEnabled)
        error.printStackTrace()
      failedProcessingFile(processInstruction, ".processorerror").get
    })
  }

  private[this] def runBasicValidationAndThenProceed(processInstruction: ProcessInstruction,
                                                     config: ValidationElement, headers: Array[String], recs: Dataset[String], footers: Array[String],
                                                     processor: Broadcast[ValidationProcessor]): Try[Boolean] = {
    // TODO: ?? empty file process ..staying in process
    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    // Check if the file is empty
    //    recs.head(1).isEmpty match {
    recs.rdd.isEmpty() match {
      case true => {
        val fileEmptyException = new OxygenException("File Empty Exception")
        logError(fileEmptyException, fullPathFileName)
        throw fileEmptyException
      }
      case false => validateFilename(recs, processInstruction, processor, config)
    }
  }

  private[this] def validateFilename(recs: Dataset[String], processInstruction: ProcessInstruction,
                                     processor: Broadcast[ValidationProcessor], config: ValidationElement): Try[Boolean] = {

    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileId = processInstruction.datasetId()
    // retrieving historic attributes based on the datasource and subscriber for each file
    val historicAttributes = if (oxygenConf.oxygen.HISTORIC_ATTRIBUTES) {
      val subscriber = processor.value.fileMetadata.subscriber
      val dataSource = processor.value.fileMetadata.dataSource
      Option(initiateHistoricAttributes(fileId, config, dataSource, subscriber))
    } else None
    val validationContext: ValidationContext = ValidationContext(BATCH, historicAttributes, None)

    logger.debug(s"Attempting to validate the filename for $fullPathFileName with processor ${processor.value.getClass.getName}")
    val validationFilename = processor.value.validateFilename(validationContext)
    val validate: Option[Validate] = SparkValidator.getValidateInfo(fullPathFileName, config.validate)
    val filenameAcceptanceExpression = if (validate.isDefined) validate.get.fileName else None
    val filenameValid = isResultValid(validationFilename, filenameAcceptanceExpression)

    val filenameValidationEvent = new FilenameValidationEvent(fileId, validationFilename.successLevel.toString, validationFilename.errors, validationFilename.result)
    KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, filenameValidationEvent.toString, fileId)

    filenameValid match {
      case true => validateHeader(recs, processInstruction, processor, config, validationContext)
      case false => {
        val filenameInvalidException = new OxygenException(s"Filename validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}")
        val msg = s"Filename validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}. Details: ${filenameValidationEvent.toString}"
        logger.error(msg)
        logger.debug(s"Filename validation failed but moving file to validated")
        validationErrorFile(config, processor, processInstruction, "errorInFileName", validationFilename, -1002, Some(msg))
        successfullyCompleteProcessingFile(processInstruction)
      }
    }
  }

  // TODO: ?? is A - ANy?
  private[this] def validateHeader[A](recs: Dataset[String], processInstruction: ProcessInstruction,
                                      processor: Broadcast[ValidationProcessor], config: ValidationElement, validationContext: ValidationContext): Try[Boolean] = {
    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileId = processInstruction.datasetId()
    logger.debug(s"Attempting to validate the header for $fullPathFileName with processor ${processor.value.getClass.getName}")

    val validateHeaderResult = processor.value.validateHeader(validationContext)
    val validate: Option[Validate] = SparkValidator.getValidateInfo(fullPathFileName, config.validate)
    val headerAcceptanceExpression = if (validate.isDefined) validate.get.header else None
    val isHeaderValid = isResultValid(validateHeaderResult, headerAcceptanceExpression)

    val headerValidationEvent = new HeaderValidationEvent(fileId, validateHeaderResult.successLevel.toString,
      validateHeaderResult.errors, validateHeaderResult.result)
    KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, headerValidationEvent.toString, fileId)
    logger.debug(headerValidationEvent.toString)

    isHeaderValid match {
      case true => validateFooter(recs, processInstruction, processor, config, validateHeaderResult.errors, validationContext)
      case false => {
        val invalidHeaderException = new OxygenException(s"Header validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}")
        val msg = s"Header validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}. Details: ${headerValidationEvent.toString}"
        logger.error(s"Header validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}")
        logger.debug(s"Header validation failed but moving file to validated")
        validationErrorFile(config, processor, processInstruction, "errorInHeader", validateHeaderResult, -1003, Some(msg))
        successfullyCompleteProcessingFile(processInstruction)
      }
    }
  }

  private[this] def validateFooter[A](recs: Dataset[String], processInstruction: ProcessInstruction,
                                      processor: Broadcast[ValidationProcessor], config: ValidationElement, headerErrors: Array[ObjectError], validationContext: ValidationContext): Try[Boolean] = {

    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileId = processInstruction.datasetId()
    logger.debug(s"Attempting to validate the footer for $fullPathFileName with processor ${processor.value.getClass.getName}")

    val validateFooterResult = processor.value.validateFooter(validationContext)
    val validate: Option[Validate] = SparkValidator.getValidateInfo(fullPathFileName, config.validate)
    val footerAcceptanceExpression = if (validate.isDefined) validate.get.footer else None
    val isFooterValid = isResultValid(validateFooterResult, footerAcceptanceExpression)
    val footerValidationEvent = new FooterValidationEvent(fileId, validateFooterResult.successLevel.toString,
      validateFooterResult.errors, validateFooterResult.result)
    KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, footerValidationEvent.toString, fileId)
    logger.debug(footerValidationEvent.toString)

    isFooterValid match {
      case true => isFileBodyValid(recs, processInstruction, processor, config, headerErrors, validateFooterResult.errors, validationContext)
      case false => {
        val invalidFooterException = new OxygenException(s"Footer validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}")
        val msg = s"Footer validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}. Details: ${footerValidationEvent.toString}"
        logger.error(s"Footer validation failed for $fullPathFileName with processor ${processor.value.getClass.getName}")
        logger.debug(s"Footer validation failed but moving file to validated")
        validationErrorFile(config, processor, processInstruction, "errorInFooter", validateFooterResult, -1004, Some(msg))
        successfullyCompleteProcessingFile(processInstruction)
      }
    }
  }

  private[this] def readFile(fullPathFileName: String, encoding: Option[String], config: ValidationElement, fileId: String): (Array[String], Dataset[String], Array[String], Array[FileValidationResult]) = {
    val fileFormat = config.fileFormat.toLowerCase()

    val (headers: Array[String], recs: Dataset[String], footers: Array[String], rowCount: Long, results: Array[FileValidationResult]) = {
      fileFormat match {
        case Constants.FILE_XML_FORMAT =>
          processXmlFile(fullPathFileName, config)
          match {
            case (Some(data), XsdValidationResult(true, start, errors)) => (data._1, data._2, data._3, data._2.count(), Array.empty[FileValidationResult])
            case (Some(data), XsdValidationResult(false, start, errors)) => {
              //report error
              (data._1, data._2, data._3, data._2.count(), errors.toArray)
            }
          }
        case Constants.FILE_AVRO_FORMAT | Constants.FILE_PARQUET_FORMAT =>
          val recs: Dataset[String] = sparkSession.read.format(fileFormat).load(fullPathFileName).toJSON.cache()
          val recCount = recs.count()
          (Array.empty[String], recs, Array.empty[String], recCount, Array.empty[FileValidationResult])
        case _ =>
          readTextOrBinaryFile(fullPathFileName, encoding, config)
      }
    }
    KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, new TotalRecordsMetrics(fileId, rowCount).toString, fileId)
    (headers, recs, footers, results)
  }

  private def readTextOrBinaryFile(fullPathFileName: String, encoding: Option[String], config: ValidationElement): (Array[String], Dataset[String], Array[String], Long, Array[FileValidationResult]) = {
    import appContext.sparkSession.implicits._

    val recs: Dataset[String] = encoding match {
      case Some(encodingValue) =>
        val charset = Try(Charset.forName(encodingValue.toUpperCase)) match {
          case Success(charset) => charset
          case Failure(exception) => throw new OxygenException(s"Unsupported Charset $encodingValue defined for the file $fullPathFileName.", exception)
        }

        readTextFileWithCharset(appContext,fullPathFileName,encodingValue.toUpperCase)
          /* appContext.sc.binaryFiles(fullPathFileName)
              .mapValues(content => {
                val fullFileContent = new String(content.toArray(), Charset.forName(encodingValue.toUpperCase))
                fullFileContent.split("\n")
              }).flatMap(_._2)*/
          .toDS()
          .cache()
      case None => appContext.sc.textFile(fullPathFileName).toDS().cache()
    }
    val rowCounts = recs.count

    val (headers, footers) = if (config.iterateAllHeaderFooter) {
      val processorContext: ProcessorContext = processors.ProcessorContext(fullPathFileName, Array(""), Array(""), OxygenLookupService.getServiceB.value)
      val processor = config.processor.get.getConstructor(processorContext.getClass)
        .newInstance(processorContext).asInstanceOf[ValidationProcessor]

      // getting data for both of header row and footer row
      val filteredHeaderData = recs.filter(each => processor.isHeaderRow(each))
      val filteredFooterData = recs.filter(each => processor.isFooterRow(each))
      val headerData = filteredHeaderData.collect()
      val footerData = filteredFooterData.collect()

      (headerData, footerData)
    }
    else {
      val headerData2 = recs.take(config.numberOfHeaderRows)
      val footerData2 = recs.rdd.zipWithIndex.filter(x => x._2 > rowCounts - (config.numberOfFooterRows + 1)).map(x => x._1).take(config.numberOfFooterRows)

      (headerData2, footerData2)
    }

    /*
        (headers, recs, footers, rowCounts, Array.empty[FileValidationResult])
      }


    KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, new TotalRecordsMetrics(fileId, rowCount).toString, fileId)

    ValidationMetricsEmits.emitHTTotalRecords(fileId, rowCount)

    (headers, recs, footers, results)
    */

    (headers, recs, footers, rowCounts, Array.empty[FileValidationResult])

  }

  private def processXmlFile(fullPathFileName: String, config: ValidationElement)
  : (Option[(Array[String], Dataset[String], Array[String], Long)], XsdValidationResult) = {
    if (!config.enableXsdValidation || config.xsdFile.isEmpty) {
      logger.info(s"XSD Validation Skipped. Configuration for XSD validation enabled = ${config.enableXsdValidation}. XSD file = ${config.xsdFile}")
      return (Some(processXml(config.headerTag,
        config.footerTag,
        config.dataTag,
        fullPathFileName,
        config.numberOfHeaderRows,
        config.numberOfFooterRows,
        config.customXmlTagHandler)), XsdValidationResult(true, "", List.empty[FileValidationResult]))
    }
    val res: Try[XsdValidationResult] = XMLValidator.validateFile(fullPathFileName, config.xsdFile.get)
    res match {
      case Success(result) => if (result.success) {
        logXsdValidationSuccess(result, config.rootTag, fullPathFileName, config.xsdFile.get)
      } else {
        logger.error(s"XSD validation error. ${result.validationException.mkString}")
      }
      case Failure(exception) =>
        val errMsg = s"Exception during XSD validation. ${exception.getLocalizedMessage}"
        val xsdValidationException : FileValidationResult = FileValidationResult(fullPathFileName, "fatal", errMsg, -1, -1)
        logError(exception, fullPathFileName, errMsg)
        return (Some(Array.empty[String], sparkSession.emptyDataset[String], Array.empty[String], 0),
          XsdValidationResult(success = false, "", List(xsdValidationException)))
    }
    (Some(processXml(config.headerTag,
      config.footerTag,
      config.dataTag,
      fullPathFileName,
      config.numberOfHeaderRows,
      config.numberOfFooterRows,
      config.customXmlTagHandler)), res.get)
  }

  private def logXsdValidationSuccess(result: XsdValidationResult, configRoot: Option[String], fullPathFileName: String, xsdFile: String) = {
    logger.info(s"Successful XSD Validation of $fullPathFileName against XSD file $xsdFile")
    var rootElement: String = result.startElement
    logger.debug(s"XML Root Element of $fullPathFileName is ${result.startElement}")
    if (configRoot.isDefined) {
      if (configRoot.get.compare(rootElement) == 0) {
        logger.debug(s"Root element configured matches schema validated root element $rootElement")
      } else {
        logger.debug(s"Root element configured ${configRoot.get} does not match schema validated root element $rootElement")
        if (rootElement.isEmpty) rootElement = configRoot.get
      }
    }
  }

  private[this] def logError(error: Throwable, fullPathFileName: String, errorMessage: String = s"") = {
    logger.error(s"Error for filePath $fullPathFileName. Error is $error. Trying next file...")
    if (logger.isDebugEnabled)
      error.printStackTrace()
  }

  private[this] def isFileBodyValid(recs: Dataset[String], processInstruction: ProcessInstruction,
                                    processor: Broadcast[ValidationProcessor], config: ValidationElement, headerErrors: Array[ObjectError],
                                    footerErrors: Array[ObjectError], validationContext: ValidationContext): Try[Boolean] = {

    Try({

      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      val fileNameOnly = FilenameUtils.getName(fullPathFileName)
      val fileId = processInstruction.datasetId()

      // Create tagged validated events
      val taggedValidatedEvents = createTaggedValidatedEvents(recs, processor, headerErrors, footerErrors, fileId, config, validationContext).persist(StorageLevel.MEMORY_AND_DISK_SER)

      // check if tagged validated events is empty, if true tag the file with error and move to validated
      if (!taggedValidatedEvents.rdd.isEmpty()) {
        processBody(taggedValidatedEvents, processInstruction, processor, config, headerErrors, footerErrors, validationContext)
        true
      }
      else {
        val invalidFileBodyException = new OxygenException(s"File body empty for $fullPathFileName with processor ${processor.value.getClass.getName}")
        val msg = s"File body empty for $fullPathFileName with processor ${processor.value.getClass.getName}. Details: File ID = $fileId"
        logger.error(msg)
        logger.debug(s"File body empty but moving file to validated")

        validationErrorFile(config, processor, processInstruction, "errorInEmptyFileBody", new ValidationError(new ValidationData, Array.empty[ObjectError]),
          -1005, Some(msg))
        successfullyCompleteProcessingFile(processInstruction)
        false
      }
    })
  }

  /**
   * Find duplicate and add QualityScore.
   * If Duplicate found, we add errorCode "OxygenDuplicate"
   *
   * @param events    - TaggedValidatedEvents dataset
   * @param frequency - frequency of this dataset
   * @return Duplicate error added to TaggedValidatedEvents
   */

  def addErrorCodesForDuplicate(events: Dataset[TaggedValidatedEvent], frequency: Frequency, deDupVersion: String, processor: Broadcast[ValidationProcessor], threshold: Integer): Dataset[TaggedValidatedEvent] = {
    logger.debug("Performing addErrorCodesForDuplicate")

    val empty = mutable.ListBuffer[(TaggedValidatedEvent)]()
    val eventsContainingDuplicates = if (deDupVersion.equalsIgnoreCase("RANDOM")) {
      //Random or Arbitary, mark all but 1 events as duplicate for a key/ picking of master events and duplicates
      events
        .rdd
        .map(x => (x.keys, x))
        .aggregateByKey(empty)(
          (lb, v) => lb += v,
          (lb1, lb2) => lb1 ++= lb2
        ).flatMap(x => {
        val key = x._1
        val eventList = x._2
        if (eventList.length > 1 && key != null && !key.isEmpty) {
          eventList.map(tve => {
            if(eventList.indexOf(tve) == 0){
              tve
            }else{
              // create new TaggedValidatedEvent with Error code value "OxygenDuplicate"
              new TaggedValidatedEvent(tve.lineage, tve.eventId, tve.validatedRecordJson,
                tve.payload,
                tve.qualityScores,
                tve.errorCodes :+ ObjectError("OxygenDuplicateError", "OxygenDuplicate", "", ""),
                tve.eventType,
                frequency,
                tve.asOfDate, tve.didCreateAsOfDateFromReceivedDate,
                tve.subscriberRelation,
                tve.subscriber,
                tve.headerErrors,
                tve.footerErrors,
                tve.keys)
            }
          })
        } else {
          eventList
        }
      })
    } else if (deDupVersion.equalsIgnoreCase("EXTN")) {
      //Add a new processor method
      events
        .rdd
        .map(x => (x.keys, x))
        .aggregateByKey(empty)(
          (lb, v) => lb += v,
          (lb1, lb2) => lb1 ++= lb2
        )
        .flatMap(x => {
          val key = x._1
          val eventList = x._2
          if (eventList.length > 1 && key != null && !key.isEmpty && eventList.length < threshold) {
            processor.value.listDuplicateEvents(eventList.toList)
          } else {
            if(eventList.length > threshold){
              logger.info(s"Duplicate events for key $key is of size $eventList.length")
            }
            eventList
          }
        })
    } else {
      //AS-IS, mark all events as duplicate for a key
      events
        .rdd
        .map(x => (x.keys, x))
        .aggregateByKey(empty)(
          (lb, v) => lb += v,
          (lb1, lb2) => lb1 ++= lb2
        )
        .flatMap(x => {
          val key = x._1
          val events = x._2
          if (events.length > 1 && key != null && !key.isEmpty) {
            // create new TaggedValidatedEvent with Error code value "OxygenDuplicate"
            events.map(tve => {
              new TaggedValidatedEvent(tve.lineage, tve.eventId, tve.validatedRecordJson,
                tve.payload,
                tve.qualityScores,
                tve.errorCodes :+ ObjectError("OxygenDuplicateError", "OxygenDuplicate", "", ""),
                tve.eventType,
                frequency,
                tve.asOfDate, tve.didCreateAsOfDateFromReceivedDate,
                tve.subscriberRelation,
                tve.subscriber,
                tve.headerErrors,
                tve.footerErrors,
                tve.keys)
            })
          } else {
            events
          }
        })
    }

    eventsContainingDuplicates.toDS()
  }

  private[this] def processBody(events: Dataset[TaggedValidatedEvent], processInstruction: ProcessInstruction,
                                processor: Broadcast[ValidationProcessor], config: ValidationElement,
                                headerErrors: Array[ObjectError], footerErrors: Array[ObjectError], validationContext: ValidationContext): Boolean = {

    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileNameOnly = FilenameUtils.getName(fullPathFileName)
    val fileId = processInstruction.datasetId()

    val contextuallyValidatedEvents = runContextualValidation(events, processor, fullPathFileName)
    val yetAnotherValidatedEvents = contextuallyValidatedEvents match {
      case Success(e) => {
        contextuallyValidateSubscriberRelation(e, processor, fullPathFileName)
      }
      case Failure(ex) => {
        logger.error(s"Unable to create validated events $fullPathFileName")
        ex.printStackTrace()
        throw ex
      }
    }

    val deDupVersion = config.deDupVersion
    val threshold = config.duplicateThreshold

    val dupCheckEnabled = config.dupKeyEnabled
    val computeMetrics = config.computeMetrics
    val metricsEvaluatedEvents = yetAnotherValidatedEvents match {
      case Success(savedValidatedEvents) => {
        if (!computeMetrics) {
          savedValidatedEvents
        } else {
          val duplicateErrorCodesAddedEvents = if (dupCheckEnabled)
            addErrorCodesForDuplicate(savedValidatedEvents, processor.value.fileMetadata.minFrequency, deDupVersion, processor, threshold)
          else
            savedValidatedEvents
          computeMetricsAndEvaluateAttributes(processor, config, processInstruction, duplicateErrorCodesAddedEvents, validationContext)
        }
      }
      case Failure(ex) => {
        logger.error(s"Unable to create validated events $fullPathFileName")
        ex.printStackTrace()
        throw ex
      }
    }

    if (metricsEvaluatedEvents.rdd.isEmpty()) {
      false
    }
    else {

      saveValidatedEvents(metricsEvaluatedEvents, processInstruction, fileNameOnly, None)

      if (computeMetrics) {
        val countOfErrorByFieldAndCode: ValidationFieldMetricEvent = ValidationMetricCalculator.calculateCountOfErrorByFieldAndCode(appContext, fileId, metricsEvaluatedEvents)
        val countOfErrorByCode: ValidationFieldMetricEvent = ValidationMetricCalculator.calculateCountOfErrorByCode(appContext, fileId, metricsEvaluatedEvents)
        val countOfErrorByFieldAndLevel = ValidationMetricCalculator.calculateCountOfErrorByFieldAndLevel(appContext, fileId, metricsEvaluatedEvents)
        val countOfErrorByLevel: Option[ValidationFieldMetricEvent] = ValidationMetricCalculator.calculateCountOfErrorByLevel(appContext, fileId, metricsEvaluatedEvents)

        KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, countOfErrorByFieldAndCode.toString, fileId)
        KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, countOfErrorByCode.toString, fileId)
        KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, String.valueOf(countOfErrorByFieldAndLevel.getOrElse("")), fileId)
        KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, String.valueOf(countOfErrorByLevel.getOrElse("")), fileId)

        if (emitStockMetricsEnabled) {
          ValidationMetricsEmits.emitHTCountByLevel(countOfErrorByLevel)
          ValidationMetricsEmits.emitHTCountByCode(Option(countOfErrorByCode))
          ValidationMetricsEmits.emitHTErrorCountByFieldAndCode(Option(countOfErrorByFieldAndCode))
          ValidationMetricsEmits.emitHTErrorCountByFieldAndLevel(countOfErrorByFieldAndLevel)
        }
      }

      metricsEvaluatedEvents.unpersist(false) // from runContextualValidation function, validatedEvents.toDS.cache()
      true

    }
  }

  private[validation] def computeMetricsAndEvaluateAttributes(processor: Broadcast[ValidationProcessor], config: ValidationElement, processInstruction: ProcessInstruction,
                                                              savedValidatedEvents: Dataset[TaggedValidatedEvent], validationContext: ValidationContext) = {
    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileNameOnly = FilenameUtils.getName(fullPathFileName)
    val fileId = processInstruction.datasetId()

    logger.debug(s"Attempting to compute metrics and evaluate attributes for $fullPathFileName with processor ${processor.value.getClass.getName}")

    calculateFinalMetricsNew(fileId, fullPathFileName, config, savedValidatedEvents) match {
      case Success(metricsEvent) => {
        val trendAddedEvents =
          if (oxygenConf.oxygen.HISTORIC_ATTRIBUTES) historicAttributes(processor, config, metricsEvent, processInstruction, savedValidatedEvents, validationContext)
          else savedValidatedEvents // if historic not enabled, return original
        trendAddedEvents
      }
    }
  }

  private def addMetricErrorCodes(validatedEvents: Dataset[TaggedValidatedEvent], validationResult: ValidationResult, processor: ValidationProcessor) = {
    import sparkSession.implicits._

    logger.debug("Performing addMetricErrorCodes")
    val newTaggedValidatedEvents = validatedEvents.map(pendingTaggedValidatedEvent => {
      val ObjErrors = validationResult.errors

      val newTaggedValidatedEvent = new TaggedValidatedEvent(pendingTaggedValidatedEvent.lineage, pendingTaggedValidatedEvent.eventId, pendingTaggedValidatedEvent.validatedRecordJson,
        pendingTaggedValidatedEvent.payload, pendingTaggedValidatedEvent.qualityScores,
        pendingTaggedValidatedEvent.errorCodes ++ ObjErrors,
        pendingTaggedValidatedEvent.eventType, processor.fileMetadata.minFrequency,
        pendingTaggedValidatedEvent.asOfDate, pendingTaggedValidatedEvent.didCreateAsOfDateFromReceivedDate,
        pendingTaggedValidatedEvent.subscriberRelation,
        pendingTaggedValidatedEvent.subscriber,
        pendingTaggedValidatedEvent.headerErrors,
        pendingTaggedValidatedEvent.footerErrors,
        pendingTaggedValidatedEvent.keys)

      newTaggedValidatedEvent
    })
    newTaggedValidatedEvents
  }

  private[validation] def historicAttributes(processor: Broadcast[ValidationProcessor], config: ValidationElement, finalMetrics: MetricsEvent, processInstruction: ProcessInstruction,
                                             savedValidatedEvents: Dataset[TaggedValidatedEvent], validationContext: ValidationContext): Dataset[TaggedValidatedEvent] = {

    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileNameOnly = FilenameUtils.getName(fullPathFileName)
    val fileId = processInstruction.datasetId()

    logger.debug("Performing historic validation")
    logger.debug(s"Attempting to find historic attributes for $fullPathFileName")
    val historic = if (validationContext.historicAttributes.isDefined) validationContext.historicAttributes.get else new JsonArray()
    //initiateHistoricAttributes(fileId, config, dataSource, subscriber)

    val metrics = finalMetrics.metrics
    // flatten the metric array and put it in a map
    val metricsArray = metrics.flatten
    val mapMetrics = new ConcurrentHashMap[String, String]()
    for ((k, v) <- metricsArray) mapMetrics.put(k, v)

    val metricData = MetricData(processor.value.fileMetadata, mapMetrics)
    val historicAttributesResult = validateHistoricResult(processor.value, metricData, historic)

    val metricAttributeAddedEvents = addMetricErrorCodes(savedValidatedEvents, historicAttributesResult, processor.value)

    val metricsValidator = new HistoricAttributesValidationEvent(fileId, historicAttributesResult.successLevel.toString, historicAttributesResult.errors, historicAttributesResult.result)
    KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, metricsValidator.toString, fileId)

    val checkedMetricAttributeAddedEvents = if (!historicAttributesResult.successful) {
      val msg = s"historic attributes validation result: failure. Details: ${metricsValidator.toString}"
      logger.debug(msg)
      val fileNameWithSuffix = fileNameOnly + ".errorInHistoricAttributes"
      saveValidatedEvents(metricAttributeAddedEvents, processInstruction, fileNameWithSuffix, Some(msg))
      emptyTaggedValidaedEvent
    }
    else {
      logger.debug("historic attributes validation result: success")
      metricAttributeAddedEvents
    }

    checkedMetricAttributeAddedEvents
  }

  /**
   * Compare historic trend with current one and past trends
   *
   * @param processor     - Validation Processor from extension
   * @param data          - Current Metric data
   * @param historicArray - historic metirc in json array
   * @return ValidationResult
   */
  private[validation] def validateHistoricResult(processor: ValidationProcessor, data: MetricData, historicArray: JsonArray): ValidationResult = {
    val historicAttributesResult = processor.validateHistoricAttributes(data, historicArray)
    historicAttributesResult
  }

  private def initiateHistoricAttributes(currentFileId: String, config: ValidationElement, dataSource: String, subscriber: String): JsonArray = {
    val pmConn = ProcessManagementClient.getOrCreate(oxygenConf)

    // Get user defined arguments for period and status
    val (periodFromRecent: Int, status: String) = if (config.historicAttributeArg.get.isEmpty) {
      // Getting default, 20 records and Success
      (20, "SUCCESS")
    } else {
      val historicAttrArgs = config.historicAttributeArg.get
      val jsonStringAsObject = new JsonParser().parse(historicAttrArgs).getAsJsonObject
      val periodFromRecent = jsonStringAsObject.get("size").getAsInt
      val status = jsonStringAsObject.get("status").getAsString
      (periodFromRecent, status)
    }

    val pastFilesResponse = pmConn.getHistoryAttributesByMultiArgs(dataSource, subscriber, status, periodFromRecent)
    // parsing all historic attributes
    val historicAttributesJsonArray = getHistoricAttributeInfo(pastFilesResponse)

    (historicAttributesJsonArray)
  }

  private def getCurrentAttributeInfo(currentIngestedItem: String) = {
    val currentAttribute = new JsonParser().parse(currentIngestedItem).getAsJsonObject.get("ingesteditems").getAsJsonArray.get(0).getAsJsonObject
    val subscriber = currentAttribute.get("datasetMetadata").getAsJsonObject.get("subscriber").getAsString
    val dataSource = currentAttribute.get("datasetMetadata").getAsJsonObject.get("dataSource").getAsString

    (dataSource, subscriber, currentAttribute)
  }

  private def getHistoricAttributeInfo(content: String) = {
    try {
      val jsonStringAsObject = new JsonParser().parse(content).getAsJsonObject.get("ingesteditems").getAsJsonArray
      jsonStringAsObject
    } catch {
      case ex: Exception => logger.warn(escapeJava("Unable to parse Historic attribures : Received " + content), ex)
        new JsonArray
    }
  }

  private[validation] def calculateFinalMetricsNew(fileId: String, fullPathFileName: String, config: ValidationElement, taggedValidatedEvents: Dataset[TaggedValidatedEvent]): Try[MetricsEvent] = {

    Try({
      logger.debug(s"Attempting to calculate final metrics for $fullPathFileName")
      val metricsElement = config.metrics.get.find(elem => fullPathFileName matches (elem.filePattern.get))
      metricsElement match {

        case None => {
          logger.debug(s"No metrics defined for $fullPathFileName")
          //throw new OxygenException(s"No metrics defined for $fullPathFileName")
          MetricsEvent(fullPathFileName, Array.empty)
        }
        case Some(metrics) => {

          val qsStats = AggregatesParserDS.createStatsDataset(appContext.sparkSession, taggedValidatedEvents).cache
          //qsStats.count

          val metricsEvents = new ListBuffer[MetricNameExprEvent]
          val metricsEventsForRetVal = new ListBuffer[Map[String, String]]

          // File level Metrics expressions
          val metricsFileLevelExpressions = metrics.file.get

          // For each metric, calculate the metric
          val aggregatesParser = new AggregatesParserDS(appContext.sparkSession.sqlContext, qsStats)

          //create a sumcache to maintain the count of fieldNames
          val sumCache = aggregatesParser.parseAll(aggregatesParser.anyExpr, config.sumMetrics)
          var valMap = Map[String, String]()
          if (sumCache.successful) {
            sumCache.get.asInstanceOf[HashMap.HashTrieMap[String, String]].foreach(x => valMap += (x._1 -> x._2))
          }

          metricsFileLevelExpressions.foreach(metricElement => {
            val metricName = metricElement.name.get
            val metricExpr = metricElement.expression.get
            try {
              val retVal: String = valMap.getOrElse(metricExpr, aggregatesParser.parseAll(aggregatesParser.anyExpr, metricExpr).getOrElse("None").toString)

              metricsEvents.append(MetricNameExprEvent(metricName, metricExpr, retVal))
              metricsEventsForRetVal.append(Map(metricName -> retVal))
            }
            catch {
              case ex: Throwable => logger.warn("Failed to parse metric expression: " + metricExpr + "\n Errormsg:" + ex.getMessage)
                //  metricsLogger.error("Failed to parse metric expression: " + metricExpr + "\n Errormsg:" + ex.getMessage)
                metricsEvents.append(MetricNameExprEvent(metricName, metricExpr, "None"))
            }
          })

          if (emitStockMetricsEnabled) ValidationMetricsEmits.emitHTCustomDatasetMetrics(fileId, metricsEvents.toList)

          qsStats.unpersist(false)
          logger.debug(s"Attempting to log metrics event and evaluate attributes for $fullPathFileName")
          val metricsMap: Map[String, Any] = metricsEvents.toList.map(m => (m.metricId, m.metricValue)).toMap
          val event = new ValidationMetricsLoggingEvent(fileId, metricsEvents.toArray)
          val eventMetricsAsMap = new ValidationDatasetMetricsLoggingEvent(fileId, metricsMap)

          logger.debug(eventMetricsAsMap.toString)
          if (oxygenConf.oxygen.isFeatureActive(Constants.features.LOG_VALIDATION_METRICS_AS_ARRAY)) {
            KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, event.toString, fileId)
          }
          KafkaMsg.sendRecordWithProperties(appContext.config, metricTopic, eventMetricsAsMap.toString, fileId)
          val eventForReturn = MetricsEvent(fullPathFileName, metricsEventsForRetVal.toArray)
          eventForReturn
        }
      }
    })
  }

  private[this] def contextuallyValidateSubscriberRelation(taggedValidatedEvents: Dataset[TaggedValidatedEvent], processor: Broadcast[ValidationProcessor], fullPathFileName: String): Try[Dataset[TaggedValidatedEvent]] = {

    Try({
      logger.debug(s"Attempting validate new subscriber relation for $fullPathFileName with processor ${processor.value.getClass.getName}")
      import sparkSession.implicits._
      val configForThisFile = validationConfig.find(elem => fullPathFileName matches elem._1).get._2.asInstanceOf[ValidationElement]
      val validate: Option[Validate] = SparkValidator.getValidateInfo(fullPathFileName, configForThisFile.validate)

      val validatedEvents = if (validate.isDefined && validate.get.contexual.validateNewSubscriberRelation) {
        logger.debug("Validating new subscriber relationship...with ")
        val subscriberRelationValidator = new ContextuallyValidateSubscriberRelation(appContext)
        val validatedEvents = subscriberRelationValidator.performSSRValidation(taggedValidatedEvents.rdd, processor)
        validatedEvents
      } else {
        logger.debug(escapeJava("Skipping contextual validation for file:" + fullPathFileName))
        taggedValidatedEvents.rdd
      }
      validatedEvents.toDS.cache() // eventsToCalculateMetricsOn.unpersist(false)
    }) // End Try()

  }

  private[this] def runContextualValidation(taggedValidatedEvents: Dataset[TaggedValidatedEvent], processor: Broadcast[ValidationProcessor], fullPathFileName: String): Try[Dataset[TaggedValidatedEvent]] = {

    Try({
      logger.debug(s"Attempting to run contextual validation for $fullPathFileName with processor ${processor.value.getClass.getName}")
      import sparkSession.implicits._
      val configForThisFile = validationConfig.find(elem => fullPathFileName matches elem._1).get._2.asInstanceOf[ValidationElement]
      val validate: Option[Validate] = SparkValidator.getValidateInfo(fullPathFileName, configForThisFile.validate)

      val validatedEvents = if (validate.isDefined && validate.get.contexual.enabled) {

        logger.debug("Performing contextual validation...with " + validate.get.contexual.version + " version.")
        logger.debug("Finding validated records using quality table that are QUALIFIED for contextual validation...")
        // Qualification criteria for contextual validation
        val recordQualificationExpression = validate.get.contexual.qualification

        // get qualified records and unqualified
        val (recordsQualifiedForContextual: RDD[TaggedValidatedEvent], recordsUnqualifiedForContextual: RDD[TaggedValidatedEvent]) = filterRecordsBasedOnCriteria(recordQualificationExpression, taggedValidatedEvents.rdd)
        /*val recordQualificationEvent = new RecordQualificationEvent(fullPathFileName, recordQualificationExpression.getOrElse("None"))
        metricsLogger.info(recordQualificationEvent.toString)*/

        val contextuallyValidatedEvents = if (validate.get.contexual.version.toLowerCase == "v4") {
          val contextualValidator = new ContextuallyValidateWithExtractor(appContext)
          contextualValidator.performContextualValidation(recordsQualifiedForContextual, processor, oxygenConf.oxygen)
        } else if (validate.get.contexual.version.toLowerCase == "v2") {
          val contextualValidator = new ContextualValidatorWithPinnedEventsV2(appContext)
          contextualValidator.performContextualValidation(recordsQualifiedForContextual, processor, oxygenConf.oxygen)
        } else if (validate.get.contexual.version.toLowerCase == "v3") {
          val contextualValidator = new ContextualValidatorWithCuratedEvents(appContext)
          contextualValidator.performContextualValidation(recordsQualifiedForContextual, processor, oxygenConf.oxygen)
        } else {
          val contextualValidator = new ContextualValidatorWithPinnedEventsV1(appContext)
          contextualValidator.performContextualValidation(recordsQualifiedForContextual, processor, oxygenConf.oxygen)
        }
        contextuallyValidatedEvents ++ recordsUnqualifiedForContextual
      } else {
        logger.debug(escapeJava("Skipping contextual validation for file:" + fullPathFileName))
        taggedValidatedEvents.rdd
      }
      validatedEvents.toDS // eventsToCalculateMetricsOn.unpersist(false)
    }) // End Try()

  }

  // TODO: saveValidatedEvents should be called from one place
  private[this] def saveValidatedEvents(ds: Dataset[TaggedValidatedEvent], processInstruction: ProcessInstruction,
                                        fileNameOnly: String, comments: Option[String]): Try[Boolean] = Try({
    val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
    val fileId = processInstruction.datasetId()

    logger.debug(s"Attempting to save validated events for $fullPathFileName")
    val fileToWrite = validationOutputDir + fileNameOnly + "." + fileId
    ioHelper.saveValidatedData(ds, fileToWrite, false)
    if (comments.isEmpty)
      orchestrationHelper.markDatasetAsSuccess(processInstruction.getJobId, processInstruction.datasetId(), fileToWrite,
        target = Option(processInstruction.target))
    else
      orchestrationHelper.markDatasetAsSuccess(processInstruction.getJobId, processInstruction.datasetId(), fileToWrite,
        comments = comments.get,
        target = Option(processInstruction.target))


  }) // End Try()

  private[this] def createTaggedValidatedEvents(recs: Dataset[String], processorB: Broadcast[ValidationProcessor], headerErrors: Array[ObjectError], footerErrors: Array[ObjectError],
                                                fileId: String, config: ValidationElement, validationContext: ValidationContext): Dataset[TaggedValidatedEvent] = {


    // Create tagged validated events from the records after stripping the header and footer
    val minFrequency = processorB.value.fileMetadata.minFrequency
    val dataSource = processorB.value.fileMetadata.dataSource
    val receivedDate = processorB.value.fileMetadata.receivedDate
    val fileName = processorB.value.fileMetadata.fileName
    val subscriber = processorB.value.fileMetadata.subscriber
    val fileAsOfDate = processorB.value.fileMetadata.fileAsOfDate

    // retrieving historic attributes based on the datasource and subscriber for each file
    val historicAttributes = validationContext.historicAttributes
    val historicAttributesBC = sparkSession.sparkContext.broadcast(historicAttributes)

    import sparkSession.implicits._

    val taggedValidatedEvents = recs.mapPartitions(recIter => {

      val processor = processorB.value
      val historicAttributesValue = historicAttributesBC.value
      recIter.
        filter(rec => !processor.isHeaderRow(rec) &&
          !processor.isFooterRow(rec) &&
          !processor.shouldFilterRow(rec)).
        flatMap(rec => {
          val recordHash = HashFunctions.getSHA1Hash(rec)
          val lineage = Lineage(dataSource, receivedDate, fileName, subscriber,
            fileAsOfDate, fileId, recordHash)

          createTaggedValidatedEvent(processor, lineage, minFrequency, rec, Map.empty[String, Object], headerErrors, footerErrors, config, historicAttributesValue)
        })
    })
    taggedValidatedEvents
  }


  private[this] def retrieveProcessorForFile(config: ValidationElement, processInstruction: ProcessInstruction, headers: Array[String], footers: Array[String], results: Array[FileValidationResult]): Try[Broadcast[ValidationProcessor]] = {

    Try({
      val fullPathFileName = processInstruction.location() // Full file path with protocol and directory name
      logger.debug(escapeJava("Trying to retrieve processor for file with full path name " + fullPathFileName))
      // Instantiate the processor.
      val processorContext: ProcessorContext = processors.ProcessorContext(fullPathFileName, headers, footers, OxygenLookupService.getServiceB.value, results)
      val processor = config.processor.get.getConstructor(processorContext.getClass)
        .newInstance(processorContext).asInstanceOf[ValidationProcessor]
      appContext.sc.broadcast(processor)
    })
  }

  private def processXml(headerTag: Option[String],
                         footerTag: Option[String],
                         dataTag: Option[String],
                         pathWithFile: String,
                         numHeader: Int,
                         numFooter: Int,
                         customTagHandler: Option[Class[_]]): (Array[String], Dataset[String], Array[String], Long) = {
    val headerFields = if (headerTag.getOrElse("").nonEmpty) { headerTag.get.split(",").toList } else List.empty[String]
    val footerFields = if (footerTag.getOrElse("").nonEmpty) { footerTag.get.split(",").toList } else List.empty[String]
    val (header, footer) = (getXmlEventHandler(customTagHandler).readTags(pathWithFile, headerFields), Array.empty[String])
    val dataDf = Try(r = appContext.sparkSession
      .read.format("com.databricks.spark.xml")
      .option("rowTag", dataTag.getOrElse(""))
      .load(pathWithFile)
      .toJSON)
    val data = dataDf match {
      case Success(value) => value
      case Failure(e) =>
        logger.error(s"Exception reading data tag $dataTag from $pathWithFile. Message ${e.getLocalizedMessage}")
        appContext.sparkSession.emptyDataset[String]
    }
    val dataCount = data.count()
    val allCount = header.length + dataCount + footer.length
    (header, data, footer, allCount)
  }

  private def getXmlEventHandler(customXmlTagHandler: Option[Class[_]]): OxygenXmlStreamReader = {
    Try({
      val tagHandler = customXmlTagHandler.getOrElse(Class.forName("com.experian.edf.oxygen.validation.xml.DefaultOxygenXmlEventReader"))
      tagHandler.newInstance().asInstanceOf[OxygenXmlStreamReader]}) match {
      case Success(value) => value
      case Failure(exception) => throw new OxygenException("OxygenXmlStreamReader could not be instantiated.", exception)
    }
  }
  private def takeRecordFromDataFrame(data: DataFrame, fieldsToRead: List[String], recordsToTake: Int) : Array[String] = {
    if (fieldsToRead.nonEmpty) {
      Some(data.select(fieldsToRead.map(col): _*).toJSON.take(recordsToTake)).getOrElse(Array.empty[String])
    } else Array.empty[String]
  }


  // process xml to get columm names and values of header, body, footer
  private def processXmlv1(rootTag: Option[String], headerTag: Option[String], footerTag: Option[String], dataTag: Option[String], pathWithFile: String, numHeader: Int, numFooter: Int): (Array[String], Dataset[String], Array[String], Long) = {
    val rootDF = getDfFromXML(rootTag, pathWithFile) // Getting the whole Dataframe with a root tag
    val headerInfo = if (!headerTag.getOrElse("").isEmpty) extractFromDF(rootDF, headerTag, appContext).toJSON.take(numHeader) else Array.empty[String]
    val footerInfo = if (!footerTag.getOrElse("").isEmpty) extractFromDF(rootDF, footerTag, appContext).toJSON.take(numFooter) else Array.empty[String]

    logger.debug("header info " + headerInfo.mkString("@#@") + "\n footer info " + footerInfo.mkString("@#@") + " \n data tag is " + dataTag.getOrElse(""))

    val dataInJson =
      if (!dataTag.getOrElse("").isEmpty()) {
        try {
          // split data tag to array .e.g : "primeData.data" convert to => Array(primeData, data)
          val splitTag = dataTag.get.split("\\.")
          // dropping unrelated tags and get only body tags and root's attribute.
          val dataDf = getDfFromFirstDataTag(rootDF.getOrElse(emptyDataFrame), splitTag(0))

          val dataDF = getEachRecordFromDataFrame(dataDf, splitTag)
          dataDF.toJSON

        } catch {
          case es: Throwable => logger.error("error when getting data DataFrame " + "\n Error msg:" + es.getMessage)
            import appContext.sparkSession.implicits._
            appContext.sparkSession.emptyDataset[String]
        }
      }
      else {
        import appContext.sparkSession.implicits._
        appContext.sparkSession.emptyDataset[String]
      }


    val dataJsonLength = dataInJson.count()
    val anyContents = headerInfo.length + footerInfo.length + dataJsonLength

    (headerInfo, dataInJson, footerInfo, anyContents)
  }


  private def processXmlv2(rootTag: Option[String], headerTag: Option[String], footerTag: Option[String], dataTag: Option[String], pathWithFile: String, numHeader: Int, numFooter: Int): (Array[String], Dataset[String], Array[String], Long) = {
    val headerInfo =
      if (!headerTag.getOrElse("").isEmpty) {
        getDfFromXML(headerTag, pathWithFile).get.toJSON.take(numHeader)
      }
      else Array.empty[String]

    val footerInfo =
      if (!footerTag.getOrElse("").isEmpty) {
        getDfFromXML(footerTag, pathWithFile).get.toJSON.take(numFooter)
      }
      else Array.empty[String]

    logger.debug("header info " + headerInfo.mkString("@#@") + "\n footer info " + footerInfo.mkString("@#@") + " \n data tag is " + dataTag.getOrElse(""))

    //    val dataDFOnly = getDfFromXML(dataTag, pathWithFile) // Getting the whole Dataframe with a root tag

    val dataInJson =
      if (!dataTag.getOrElse("").isEmpty()) {
        try {
          // split data tag to array .e.g : "primeData.data" convert to => Array(primeData, data)
          val splitTag = dataTag.get.split("\\.")
          // dropping unrelated tags and get only body tags and root's attribute.
          val dataDf = getDfFromXML(dataTag, pathWithFile).get

          dataDf.toJSON

        } catch {
          case es: Throwable => logger.error("error when getting data DataFrame " + "\n Errormsg:" + es.getMessage)
            import appContext.sparkSession.implicits._
            appContext.sparkSession.emptyDataset[String]
        }

      }
      else {
        import appContext.sparkSession.implicits._
        appContext.sparkSession.emptyDataset[String]
      }


    val dataJsonLength = dataInJson.count()
    val anyContents = headerInfo.length + footerInfo.length + dataJsonLength

    (headerInfo, dataInJson, footerInfo, anyContents)
  }

  private def getDfFromFirstDataTag(df: DataFrame, dataTag: String) = {
    // get columns those are not the data tag, and attributes of root and convert to Seq[String]
    val notDataTags = df.columns.filter(each => !each.startsWith("_") && !each.startsWith(dataTag)).toSeq
    val filteredDF = df.select(df.columns.filter(colName => !notDataTags.contains(colName)).map(colName => new Column(colName)): _*)

    filteredDF
  }

  // get all child's attributes, here starts with "_", and flatten with "getEachAttribute" method
  private def flattenSchema(df: DataFrame, currentTag: String = null, nextTag: String) = {
    val nextAttributes = df.select(currentTag + ".*").columns.filter(each => each.startsWith("_"))
    val flattenDf = getEachAttribute(df, currentTag, nextTag, nextAttributes)
    flattenDf
  }

  // get Dataframe in a situation when there are multiple tags for each record.
  // We would like to get each record, and it is grouped by three different parent tag with unique attributes for each
  // <root>
  // <data att1="Client type1"> <record>data1</record> </data>
  // <data att1="Client type2"> <record>data2</record> </data>
  // <data att1="Client type3"> <record>data2</record> </data>
  // </root>
  //
  //  Given Array "a"
  // {
  //  "a": [1, 2]
  // }
  // With explode, create a new row(s) for each array / map for the
  // explode("a").alias("x"))) // will make like the following
  //  x
  // ----
  //  1
  //  2
  //
  // TODO : need to support same attribute name that might exist from parent to child attributes

  def getEachRecordFromDataFrame(df: DataFrame, dataTag: Array[String]): DataFrame = {
    val firstTag: String = if (!dataTag.isEmpty) dataTag(0) else ""
    // if it is struct, we will flatten the schema to an array
    // otherwise, we will explode and flatten
    val schemaType: String = if (!dataTag.isEmpty) {
      df.select(firstTag).schema.fields(0).dataType.typeName
    } else {
      ""
    }
    logger.info("schema type  " + schemaType + " length " + dataTag.length)

    // until it reaches the last tag, we flatten schema
    dataTag.length match {
      case x if x >= 1 =>
        if (schemaType.equalsIgnoreCase("struct")) {
          // if it is the last tag, we are done, otherwise bring child elements to parents level
          if (x == 1) getEachRecordFromDataFrame(df, dataTag.drop(1))
          // otherwise bring child elements to parents level
          else getEachRecordFromDataFrame(flattenSchema(df, firstTag, dataTag(1)), dataTag.drop(1))
        } else { // schema is array, so we will explode it
          val explodedDafaFrame = df.withColumn(firstTag, explode(col(firstTag)))
          // if it is the last tag, we are done
          if (x == 1) getEachRecordFromDataFrame(explodedDafaFrame, dataTag.drop(1))
          // otherwise bring child elements to parents level
          else getEachRecordFromDataFrame(flattenSchema(explodedDafaFrame, firstTag, dataTag(1)), dataTag.drop(1))
        }
      case 0 =>
        df
    }
  }

  // take out next column including attributes. Calling recursively to get each attribute and attach it to DF
  private def getEachAttribute(df: DataFrame, currentTagName: String, nextTagName: String, attr: Array[String]): DataFrame = {
    if (attr.length == 0) df.withColumn(nextTagName, col(currentTagName + "." + nextTagName)).drop(currentTagName)
    else {
      val attrAddedDf = df.withColumn(currentTagName + attr(0), col(currentTagName + "." + attr(0)))
      val droppedFirstTag = attr.drop(1)
      getEachAttribute(attrAddedDf, currentTagName, nextTagName, droppedFirstTag)
    }
  }


  // get Dataframe from xml
  private def getDfFromXML(tag: Option[String], fullPathFileName: String): Option[DataFrame] = {
    if (tag.getOrElse("") == "") None
    else { // We are getting structtype with disabled infer type.
      val stringSchema = appContext.sparkSession.sqlContext.read.format("com.databricks.spark.xml").option("rowTag", tag.getOrElse("")).load(fullPathFileName).schema
      Some(appContext.sparkSession.sqlContext.read.format("com.databricks.spark.xml").option("rowTag", tag.getOrElse("")).schema(stringSchema).load(fullPathFileName))
    }
  }

  // we are passing non empty DataFrame, and non empty tag, and we are getting dataFrame from a given tag
  private def extractFromDF(df: Option[DataFrame], tag: Option[String], appContext: SparkOxygenAppContext): DataFrame = {
    try {
      df.get.select(tag.getOrElse("") + ".*")
    } catch {
      case ex: Throwable => logger.error("Failed to convert dataframe to Json format for header/footer: " + "\n Errormsg:" + ex.getMessage)
        emptyDataFrame
    }
  }


  private[oxygen] def shouldContextuallyValidate(filename: String,
                                                 filenamePatterns: List[String],
                                                 shouldContextuallyValidateFlagsMap: ConcurrentHashMap[String, String]): Boolean = {

    for (pattern <- filenamePatterns) {
      if (filename.matches(pattern)) {
        return shouldContextuallyValidateFlagsMap.get(pattern).toBoolean
      }
    }
    true
  }

  /**
   * Filter records for contextual validation using given record qualification/acceptance criteria
   *
   * @param taggedEvents
   * @return
   */
  def filterRecordsBasedOnCriteria(expression: Option[String], taggedEvents: RDD[TaggedValidatedEvent]): (RDD[TaggedValidatedEvent], RDD[TaggedValidatedEvent]) = {

    val criteriaMatchedRecords = taggedEvents.filter(eachEvent => {
      SparkValidator.isRecordValid(eachEvent, expression)
    })

    val criteriaUnmatchedRecords = taggedEvents.filter(eachEvent => {
      !SparkValidator.isRecordValid(eachEvent, expression)
    })
    // return qualified and unqualified records
    (criteriaMatchedRecords, criteriaUnmatchedRecords)
  }

}

private[oxygen] object CustomSparkValidator extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  val validationProcessorMap = new ConcurrentHashMap[String, ValidationProcessor]

  /**
   *
   */
  val isRecordValid: (TaggedValidatedEvent, Option[String]) => Boolean = (validationValue, expression) => {

    // Validate record using the parser. If there is an expression defined for the file,
    //  look for the errorCode and quality score
    if ((expression match {
      case None => false
      case default => true
    }) && !expression.get.isEmpty) {
      val parser = RecordValidationParser(Left(validationValue))
      val parseResult = parser.parseAll(parser.boolExpr, expression.get).get
      parseResult
    }
    else
      true
  }

  def getValidateInfo(fillPathFileName: String, validates: Option[List[Validate]]): Option[Validate] = {
    for (data <- validates.getOrElse(List.empty)) {
      if (fillPathFileName.matches(data.filePattern.get))
        return Some(data)
    }
    None
  }

  def getMetricsInfo(fillPathFileName: String, metrics: Option[List[Metrics]]): Option[Metrics] = {
    for (data <- metrics.getOrElse(List.empty)) {
      if (fillPathFileName.matches(data.filePattern.get))
        return Some(data)
    }
    None
  }

  def readTextFileWithCharset(context: SparkOxygenAppContext, location: String, charset: String) : RDD[String] = {

    val DEFAULT_CHARSET = Charset.forName("UTF-8")
    if (Charset.forName(charset) == DEFAULT_CHARSET) {
      context.sc.textFile(location)
    } else {
      context.sc.hadoopFile[LongWritable, Text, TextInputFormat](location).map(
        pair => new String(pair._2.getBytes, 0, pair._2.getLength, charset)
      )
    }


  }

}