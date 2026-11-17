package com.experian.edf.oxygen.pinning.util

import org.apache.spark.sql.SparkSession

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 12/18/19 at 11:02 AM
 */
case class OxygenIOUtils(spark: SparkSession) extends Serializable {
  val read = new OxygenIOReaderUtils(spark)
  val write = new OxygenIOWriterUtils(spark)
}
