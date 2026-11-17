package com.experian.edf.oxygen.consistencycheck.engines.io

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.Constants
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 12/17/19 at 11:29 AM
 */
private[oxygen] class OxygenIOWriter(sparkSession: SparkSession) extends Serializable {

  def fromDF(data: DataFrame, location: String, format: String, partitionedBy: Option[String] = None): Boolean = {
    try {
      val write = if (partitionedBy.isDefined) data.write.partitionBy(partitionedBy.get) else data.write
      format match {
        case Constants.FILE_AVRO_FORMAT => write.mode("overwrite").format("avro").save(location)
        case Constants.FILE_PARQUET_FORMAT => write.mode("overwrite").parquet(location)
        case _ => write.mode("overwrite").json(location)
      }
      true
    } catch {
      case e: Throwable => throw new OxygenException(s"Unable to write Dataframe to $location : ${e.getMessage}", e)
    }
  }

  def fromDS[T](data: Dataset[T], location: String, format: String, partitionedBy: Option[String] = None): Boolean = {
    try {
      val write = if (partitionedBy.isDefined) data.write.partitionBy(partitionedBy.get) else data.write
      format match {
        case Constants.FILE_AVRO_FORMAT => write.mode("overwrite").format("avro").save(location)
        case Constants.FILE_PARQUET_FORMAT => write.mode("overwrite").parquet(location)
        case _ => write.mode("overwrite").json(location)
      }
      true
    } catch {
      case e: Throwable => throw new OxygenException(s"Unable to write Dataset to $location : ${e.getMessage}", e)
    }
  }
}
