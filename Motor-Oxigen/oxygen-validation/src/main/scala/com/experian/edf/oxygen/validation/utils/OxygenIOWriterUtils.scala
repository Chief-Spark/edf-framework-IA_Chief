package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedValidatedEvent, TaggedWritableEvent}
import com.experian.edf.oxygen.io.OxygenIO
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, Dataset, Encoder, SparkSession}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 12/18/19 at 11:02 AM
 */
class OxygenIOWriterUtils(spark: SparkSession) extends Serializable {
  private val oxygenIO = new OxygenIO(spark)

  def dataExtractorTCE(data: Dataset[TaggedCuratedEventWithMap], location: String, format: String, partitionedBy: Option[String] = None): Boolean = {
    implicit val taggedCuratedEventEncoder: Encoder[TaggedWritableEvent] = org.apache.spark.sql.Encoders.product[TaggedWritableEvent]
    val writableEvent = data.map(TaggedWritableEvent.fromTaggedCuratedEvent)
    val partitionedWritableEvent: Dataset[TaggedWritableEvent] =  writableEvent
    val isDataExtractorWriteSuccess = dataSet(partitionedWritableEvent, location, format,partitionedBy)
    data.unpersist(blocking = false)
    isDataExtractorWriteSuccess
  }

  def dataFrame(data: DataFrame, location: String, format: String, partitionedBy: Option[String] = None): Boolean = oxygenIO.write.fromDF(data, location, format, partitionedBy)

  def dataSet[T](data: Dataset[T], location: String, format: String, partitionedBy: Option[String] = None): Boolean = oxygenIO.write.fromDS(data, location, format, partitionedBy)

  def asTextFile(data: RDD[String], location: String) = data.saveAsTextFile(location)

  def taggedValidatedEvents(data: Dataset[TaggedValidatedEvent], location: String, format: String, partitionedBy: Option[String]): Boolean = {
    dataSet(data, location, format, partitionedBy)
  }

}
