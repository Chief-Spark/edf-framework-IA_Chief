package com.experian.edf.oxygen.validation.context

import java.io.FileNotFoundException
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.OxygenPipelineConfiguration
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.domain.{TaggedCuratedEvent, TaggedValidatedEvent}
import com.experian.edf.oxygen.domain.internal.{ContextPinMapEvent, PinnedEvent}
import com.experian.edf.oxygen.utils.{JsonUtils, OxygenFileUtils}
import com.experian.edf.oxygen.validation.domain.ValidationProcessor
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD

private[validation] abstract class ContextualValidatorWithPinnedEvents(appContext: SparkOxygenAppContext) extends Serializable {

  def performContextualValidation(pendingValidatedEvents: RDD[TaggedValidatedEvent],
                                  processor: Broadcast[ValidationProcessor], pipelineConfiguration: OxygenPipelineConfiguration): RDD[TaggedValidatedEvent]

  private[validation] def readContextPinMap(pipelineConfiguration: OxygenPipelineConfiguration): RDD[(String, Long)] = {
    val (pinnedDir, sc) = (pipelineConfiguration.PINNED_DIRECTORY, appContext.sc)
    val pinMapFile = currentContextPinMapFilename(pipelineConfiguration)
    val contextPinMap = if (pinMapFile != null) {
      val contextPinMapRecs = sc.textFile(pinMapFile)
      contextPinMapRecs.map(x => {
        val contextPinMap = new ContextPinMapEvent(x)
        (contextPinMap.context, contextPinMap.pin)
      })
    } else {
      null
    }
    contextPinMap
  }

  private[validation] def readPinnedEvent(pipelineConfiguration: OxygenPipelineConfiguration): RDD[(Long, TaggedCuratedEvent)] = {
    val (pinnedDir, sc) = (pipelineConfiguration.PINNED_DIRECTORY, appContext.sc)

    val storageFormat = pipelineConfiguration.FILE_STORAGE_FORMAT
    val compressionFormat = pipelineConfiguration.FILE_COMPRESSION_FORMAT

    val pinnedRecords = if (storageFormat == Constants.FILE_AVRO_FORMAT) {
      appContext.sparkSession.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
      try {
        val pinnedEventsDF = appContext.sparkSession.read.format("avro").load(currentPinFilename(pipelineConfiguration))
        pinnedEventsDF.toJSON.rdd
      }
      catch {
        case fne: FileNotFoundException => {
          appContext.sparkSession.emptyDataFrame.toJSON.rdd
        }
      }
    }
    else if (storageFormat == Constants.FILE_PARQUET_FORMAT) {
      try {
        val pinnedEventsDF = appContext.sparkSession.read.parquet(currentPinFilename(pipelineConfiguration))
        pinnedEventsDF.toJSON.rdd
      }
      catch {
        case fne: FileNotFoundException => {
          appContext.sparkSession.emptyDataFrame.toJSON.rdd
        }
      }
    }
    else {
      val pinnedEventsDF = appContext.sparkSession.read.json(currentPinFilename(pipelineConfiguration))
      pinnedEventsDF.toJSON.rdd
    }


    // val pinnedRecs = sc.textFile(currentPinFilename)
    pinnedRecords.map(x => {
      val pinnedEvent = JsonUtils.fromJson(x, classOf[PinnedEvent]) // new PinnedEvent(x)
      (pinnedEvent.pin, pinnedEvent.taggedCuratedEvent)
    })
  }

  private[validation] def currentPinFilename(pipelineConfiguration: OxygenPipelineConfiguration): String = {
    val pinDir = pipelineConfiguration.PINNED_DIRECTORY
    val fileUtils = OxygenFileUtils.create(appContext)

    val files = fileUtils.listDirectories(pinDir).filter(_.matches(".*-pin"))

    if (files.length != 1) null
    else files(0)
  }

  private[validation] def currentContextPinMapFilename(pipelineConfiguration: OxygenPipelineConfiguration): String = {
    val pinDir = pipelineConfiguration.PINNED_DIRECTORY
    val fileUtils = OxygenFileUtils.create(appContext)

    val files = fileUtils.listDirectories(pinDir).filter(_.matches(".*-context-pin-map"))

    if (files.length != 1) null
    else files(0)
  }

}
