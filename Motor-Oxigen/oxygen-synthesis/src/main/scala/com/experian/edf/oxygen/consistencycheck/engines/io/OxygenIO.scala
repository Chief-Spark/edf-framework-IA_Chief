package com.experian.edf.oxygen.consistencycheck.engines.io

import org.apache.spark.sql.SparkSession


/**
 *
 * @author Abraham, Thomas - C15627Aa
 *         Created on 10/31/19 at 7:44 AM
 */
class OxygenIO(sparkSession: SparkSession) extends Serializable {
  sparkSession.sparkContext.hadoopConfiguration.set("avro.mapred.ignore.inputs.without.extension", "false")
  val read = new OxygenIOReader(sparkSession)
  val write = new OxygenIOWriter(sparkSession)
}
