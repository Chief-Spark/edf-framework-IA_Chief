package com.experian.edf.oxygen.curation.engines.io.utils

import com.experian.edf.oxygen.curation.domain.internal.SubscriberSubscriberRelation
import com.experian.edf.oxygen.domain.{HeterogeneousCollection, PinnedEventWithMap, TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedValidatedEvent, TaggedWritableEvent}
import com.experian.edf.oxygen.io.OxygenIO
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, Dataset, Encoder, SparkSession}
import org.apache.spark.storage.StorageLevel

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 12/18/19 at 11:02 AM
 */
class OxygenIOWriterUtils(spark: SparkSession) extends Serializable {
  private val oxygenIO = new OxygenIO(spark)

  def dataFrame(data: DataFrame, location: String, format: String, partitionedBy: Option[String] = None): Boolean = oxygenIO.write.fromDF(data, location, format, partitionedBy)

  def dataSet[T](data: Dataset[T], location: String, format: String, partitionedBy: Option[String] = None): Boolean = oxygenIO.write.fromDS(data, location, format, partitionedBy)

  def rdd[T](data: RDD[T], location: String, format: String): Boolean = {
    val frame = spark.read.json(data.map(each => ScalaJacksonUtil.toJson(each).get))
    oxygenIO.write.fromDF(frame, location, format)
  }


  def asTextFile(data: RDD[String], location: String) = data.saveAsTextFile(location)

  def heterogeneousCollection(data: RDD[HeterogeneousCollection], location: String, format: String): Boolean = {
    val frame = spark.read.json(data.map(each => ScalaJacksonUtil.toJson(each).get))
    oxygenIO.write.fromDF(frame, location, format)
  }

  def taggedCuratedEventsWithMap(data: Dataset[TaggedCuratedEventWithMap], location: String, format: String, partitions: Option[Int], ssrLocation: Option[String] = None): Boolean = {
    implicit val taggedCuratedEventEncoder: Encoder[TaggedWritableEvent] = org.apache.spark.sql.Encoders.product[TaggedWritableEvent]
    import spark.implicits._
    val writableEvent = data.map(TaggedWritableEvent.fromTaggedCuratedEvent)
    val partitionedWritableEvent: Dataset[TaggedWritableEvent] = if (partitions.isDefined) writableEvent.repartition(partitions.get, col("eventId")) else writableEvent
    val isSSRWriteSuccess = if (ssrLocation.isDefined) {
      // TODO: should be optimized, removing due to GC problem 
      //data.persist(StorageLevel.MEMORY_AND_DISK_SER)
      dataSet(data.map(each => SubscriberSubscriberRelation(each.subscriber, each.subscriberRelation)).distinct, ssrLocation.get, "parquet")
    } else true
    val isCurationWriteSuccess = dataSet(partitionedWritableEvent, location, format)
    //data.unpersist(blocking = false)
    isCurationWriteSuccess && isSSRWriteSuccess
  }


  def taggedValidatedEvents(data: Dataset[TaggedValidatedEvent], location: String, format: String, partitionedBy: Option[String]): Boolean = {
    dataSet(data, location, format, partitionedBy)
  }

  def taggedWritableEvent(data: Dataset[TaggedWritableEvent], location: String, format: String): Boolean = dataSet(data, location, format)

  def taggedCuratedEvents(data: Dataset[TaggedCuratedEvent], location: String, format: String): Boolean = dataSet(data, location, format)

  def pinnedEventWithMap(data: RDD[PinnedEventWithMap], location: String, format: String): Boolean = {
    data.persist(StorageLevel.MEMORY_AND_DISK_SER)
    val value: RDD[String] = data.map(each => ScalaJacksonUtil.toJson(each).get)
    val frame = spark.read.json(value)
    oxygenIO.write.fromDF(frame, location, format)
  }

  // def taggedValidatedEvents(data: Dataset[TaggedValidatedEvent], location: String, format: String): Boolean = dataSet(data, location, format)

}
