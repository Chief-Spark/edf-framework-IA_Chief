package com.experian.edf.oxygen.consistencycheck.engines.metric

import com.experian.edf.oxygen.consistencycheck.engines.utils.AggregatesParserDF
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import org.apache.spark.sql.{DataFrame, SQLContext}


object ConsistencyMetricParser {

  def createQSDataFrame(appContext: SparkOxygenAppContext, statsDFAll: DataFrame): DataFrame = {

    import appContext.sparkSession.implicits._

    val reportStatsDF = statsDFAll.withColumn("lineage", $"lineage")
      .withColumn("info", $"consistencyInfo")
      .withColumn("filename", $"eventlineage.fileName")

    reportStatsDF
  }

  def destroyQSDataFrame(appContext: SparkOxygenAppContext, statsRDD: DataFrame) = {
    statsRDD.unpersist
  }

}

class ConsistencyMetricParser(sqlCtx: SQLContext, statsRDD: DataFrame) extends AggregatesParserDF(sqlCtx, statsRDD) {

  type LS = List[String]

  def getPinDataForFile: Parser[LS] = "getPinDataForFile(" ~ stringLiteral ~ ")" ^^ { case "getPinDataForFile(" ~ file ~ ")" => {
    statsRDD.createOrReplaceTempView("filepins")
    val retval = sqlCtx.sql("select pin from filepins where filename = "+ file)
    retval.collect.map(row => row.toString()).toList
  }

  }

  // TODO: aggregate by multiple fields and count 
  // TODO: more expressions to be added 

  override def aggExpr: Parser[A] = super.aggExpr | getPinDataForFile

}