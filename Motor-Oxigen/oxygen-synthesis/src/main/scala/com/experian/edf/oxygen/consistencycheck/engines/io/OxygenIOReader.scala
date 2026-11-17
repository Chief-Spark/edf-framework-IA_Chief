package com.experian.edf.oxygen.consistencycheck.engines.io

import com.experian.edf.oxygen.base.Constants
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, DataFrameReader, SparkSession}
import org.slf4j.LoggerFactory.getLogger

private[oxygen] class OxygenIOReader(spark: SparkSession) extends Serializable {

  @transient private lazy val logger = getLogger(this.getClass)
  private val AVRO_FORMAT: String = "avro"

  spark.sqlContext.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "true")

  def dataframe(file: String, format: String, schema: Option[StructType] = None): DataFrame = dataframe(List(file), format, schema)

  def dataframe(files: List[String], format: String): DataFrame = dataframe(files, format, None)

  /**
   * Thing wrapper around spark's read functionality for reading data into a DataFrame.
   *
   * This method will *not* handle spark errors, which should be dealt with
   * by the caller as appropriate.
   *
   * @param files A path to a file or folder which should be loaded
   * @param format The format of the data to be read. This method currently
   *               supports the FILE_AVRO_FORMAT and FILE_PARQUET_FORMAT constants
   *               as defined in com.experian.edf.oxygen.base.Constants. Any other
   *               value results as in the data being treated as JSON.
   * @param schema An optional schema to be applied to the data on read.
   * @return A dataframe representing the data at the provided path.
   */
  def dataframe(files: List[String], format: String, schema: Option[StructType]): DataFrame = {
    val dsReader: DataFrameReader = if (schema.isDefined) spark.read.schema(schema.get) else spark.read
    format match {
      case Constants.FILE_AVRO_FORMAT => dsReader.format(AVRO_FORMAT).load(files: _*)
      case Constants.FILE_PARQUET_FORMAT => dsReader.parquet(files: _*)
      case _ => dsReader.json(files: _*)
    }
  }

}
