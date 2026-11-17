package com.experian.edf.oxygen.validation.context

import com.experian.edf.oxygen.base.{Constants, OxygenService}
import com.experian.edf.oxygen.configuration.configs.OxygenPipelineConfiguration
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent}
import com.experian.edf.oxygen.validation.domain.ValidationProcessor
import com.experian.edf.oxygen.validation.utils.DataExtractorIOUtils
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.AnalysisException
import org.apache.spark.storage.StorageLevel
import org.slf4j.LoggerFactory.getLogger

import java.io.File

private[validation] trait ExtractedEventReader {
  def readExtractedEvent(appContext: SparkOxygenAppContext, dataExtractorIOUtils: DataExtractorIOUtils): RDD[TaggedCuratedEventWithMap]
}

private[validation] object readExtractedEventReaderImpl extends ExtractedEventReader {
  override def readExtractedEvent(appContext: SparkOxygenAppContext, dataExtractorIOUtils: DataExtractorIOUtils): RDD[TaggedCuratedEventWithMap] = {
    val cvExtractorDir = appContext.config.oxygen.CONTEXTUAL_VALIDATION_EXTRACT_DIRECTORY
    val extractorDir = if (cvExtractorDir != null || !cvExtractorDir.isEmpty || cvExtractorDir != None) cvExtractorDir + File.separator + "*" else appContext.config.oxygen.stage.dataExtractor.get.paths.dataExtractorExtractDir + File.separator + "*"
    dataExtractorIOUtils.readDataExtractorData(extractorDir, Constants.PARQUET_FORMAT)
  }
}

class ContextuallyValidateWithExtractor(appContext: SparkOxygenAppContext,
                                        extractorReader: ExtractedEventReader = readExtractedEventReaderImpl) extends OxygenService(appContext) {

  @transient private lazy val logger = getLogger(this.getClass)
  val dataExtractorIOUtils = new DataExtractorIOUtils(fileUtils, appContext)

  private[validation] def performContextualValidation(
                                                       pendingValidatedEvents: RDD[TaggedValidatedEvent],
                                                       processor: Broadcast[ValidationProcessor],
                                                       pipelineConfiguration: OxygenPipelineConfiguration
                                                     ): RDD[TaggedValidatedEvent] = {

    val contextColumns = processor.value.contextPredicates
    val subscriber = processor.value.fileMetadata.subscriber

    if (contextColumns == Map.empty) {
      pendingValidatedEvents
    }
    else {
      val tceLite = {
        try {
          extractorReader.readExtractedEvent(appContext, dataExtractorIOUtils)
        } catch {
          case e: AnalysisException if e.getMessage.startsWith("Path does not exist") => {
            logger.warn("No events found for contextual validation. Continuing with pending validated events only")
            appContext.sc.emptyRDD[TaggedCuratedEventWithMap]
          }
        }
      }.persist(StorageLevel.MEMORY_AND_DISK_SER)


      if (tceLite.isEmpty()) {
        logger.warn("No events found for contextual validation. Continuing with pending validated events only")
        pendingValidatedEvents
      } else {
        logger.info("Events found for contextual validation. Continuing with CV V4")
        val filterEvents = tceLite.filter(x => x.subscriber == subscriber)
        ContextualValidator.validateContextually(
          contextColumns,
          processor,
          pendingValidatedEvents,
          filterEvents,
          appContext
        )
      }
    }
  }

}
