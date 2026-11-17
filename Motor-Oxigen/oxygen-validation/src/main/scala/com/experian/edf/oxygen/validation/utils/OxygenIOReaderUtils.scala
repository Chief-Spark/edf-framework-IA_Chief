package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedWritableEvent}
import com.experian.edf.oxygen.io.OxygenIO
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.ScalaReflection
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, SparkSession}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 12/18/19 at 11:02 AM
 */
class OxygenIOReaderUtils(spark: SparkSession) extends Serializable {
  private val oxygenIO = new OxygenIO(spark)

  import spark.implicits._

  def dataframe(file: String, format: String, schema: Option[StructType]): DataFrame = oxygenIO.read.dataframe(file, format, schema)

  def dataframe(file: String, format: String): DataFrame = oxygenIO.read.dataframe(file, format, None)

  def dataframe(file: List[String], format: String): DataFrame = oxygenIO.read.dataframe(file, format, None)


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
