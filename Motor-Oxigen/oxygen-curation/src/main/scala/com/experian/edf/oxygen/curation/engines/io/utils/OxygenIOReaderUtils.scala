package com.experian.edf.oxygen.curation.engines.io.utils

import com.experian.edf.oxygen.domain.internal.events.ValidatedEvent
import com.experian.edf.oxygen.domain.pinning.PinnedTaggedIdentity
import com.experian.edf.oxygen.domain.{HeterogeneousCollection, HeterogeneousWritableCollection, PinnedEventWithMap, TaggedCuratedEventWithMap, TaggedValidatedEvent, TaggedWritableEvent}
import com.experian.edf.oxygen.io.OxygenIO
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.ScalaReflection
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 12/18/19 at 11:02 AM
 */
class OxygenIOReaderUtils(spark: SparkSession) extends Serializable {
  private val oxygenIO = new OxygenIO(spark)

  import spark.implicits._

  def dataframe(file: String, format: String, schema: Option[StructType]): DataFrame = oxygenIO.read.dataframe(file, format, schema)

  def dataframe(file: List[String], format: String, schema: Option[StructType]): DataFrame = oxygenIO.read.dataframe(file, format, schema)

  def dataframe(file: String, format: String): DataFrame = oxygenIO.read.dataframe(file, format, None)

  def dataframe(file: List[String], format: String): DataFrame = oxygenIO.read.dataframe(file, format, None)

  def pinnedEventWithMap(file: String, format: String): RDD[PinnedEventWithMap] = {
    val frame = oxygenIO.read.dataframe(file, format)
    frame.toJSON.rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[PinnedEventWithMap]).get)
  }

  def pinnedTaggedIdentity( file: String, format: String): RDD[PinnedTaggedIdentity] = {
    val frame = oxygenIO.read.dataframe(file, format)
    frame.toJSON.rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[PinnedTaggedIdentity]).get)
  }

  def validatedEvents(file: List[String], format: String): Dataset[ValidatedEvent] = dataframe(file, format, Option(ScalaReflection.schemaFor[ValidatedEvent].dataType.asInstanceOf[StructType])).as[ValidatedEvent]

  def taggedWritableEvents(file: String, format: String): Dataset[TaggedWritableEvent] = dataframe(file, format).as[TaggedWritableEvent]

  def heterogeneousCollection(file: String, format: String): RDD[HeterogeneousCollection] = heterogeneousWritableCollection(file, format).rdd.map(HeterogeneousWritableCollection.toHeterogeneousCollection)

  def heterogeneousWritableCollection(file: String, format: String): Dataset[HeterogeneousWritableCollection] = {
    val schema: Option[StructType] = Option(ScalaReflection.schemaFor[HeterogeneousWritableCollection].dataType.asInstanceOf[StructType])
    dataframe(file, format,schema).as[HeterogeneousWritableCollection]
  }

  def taggedValidatedEvents(file: String, format: String): Dataset[TaggedValidatedEvent] = {
    val frame = dataframe(file, format)
    frame.as[TaggedValidatedEvent]
  }

  def taggedCuratedEventsWithMap(file: String, format: String): RDD[TaggedCuratedEventWithMap] = {
    val frame = dataframe(file, format)
    frame.toJSON.rdd.map(each => ScalaJacksonUtil.fromJson(each, classOf[TaggedCuratedEventWithMap]).get)
  }

  /**
   * Read the given file containing TaggedCuratedEventWithMap objects represented
   * in the given format and return an RDD of TaggedCuratedEventWithMap objects.
   *
   * If there are no files at the given path, then an error is thrown.
   *
   * @param file The path to a file containing TaggedCuratedEventWithMap representations
   * @param format The format of the file
   * @return An RDD of TaggedCuratedEventWithMap objects
   */
  def taggedCuratedEventsFromWritable(file: String, format: String): RDD[TaggedCuratedEventWithMap] = {
    val schema: Option[StructType] = Option(ScalaReflection.schemaFor[TaggedWritableEvent].dataType.asInstanceOf[StructType])
    dataframe(file, format, schema).as[TaggedWritableEvent].rdd.map(TaggedWritableEvent.toTaggedCuratedEvent)
  }
}
