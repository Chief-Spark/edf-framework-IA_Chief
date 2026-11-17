package com.experian.edf.oxygen.acceptance.util

import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.context.SparkOxygenAppContext
import com.experian.edf.oxygen.processmngt.ProcessManagementClient
import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, Row, SQLContext}

object ExternalActionParser {

  def createQSDataFrame(appContext: SparkOxygenAppContext, statsDFAll: DataFrame): DataFrame = {

    import appContext.sparkSession.implicits._

    val qualityScoreArrayToMap = udf[Map[String, Double], Seq[Row]] { array => array.map { case Row(name: String, score: Double) => (name, score) }.toMap }
    val errorCodesArrayToMap = udf[Map[String, String], Seq[Row]] { array => array.map { case Row(errorCodeName: String, errorCodeValue: String, errorType: String, messageKey: String) => (errorCodeName, errorCodeValue) }.toMap }

    val statsDF = statsDFAll.withColumn("qs", qualityScoreArrayToMap($"qualityScore"))
      .withColumn("rec", $"validatedRecord")
      .withColumn("err", errorCodesArrayToMap($"errorCodes"))
      .cache()
    statsDF
  }

  def destroyQSDataFrame(appContext: SparkOxygenAppContext, statsRDD: DataFrame) = {
    statsRDD.unpersist
  }

}

class ExternalActionParser(oxygenConfig: OxygenConfig, sqlCtx: SQLContext, statsRDD: DataFrame, fileId: String) extends AggregatesParserDF(sqlCtx, statsRDD) {


  def externalAction: Parser[S] = "externalAction(" ~ stringLiteral ~ ")" ^^ { case "externalAction(" ~ acceptExp ~ ")" => {
    //val extValue: Option[ExternalAction] = OxygenPersistance.externalAction.findByFileId(fileId.toLong)
    val extValue: Option[Map[String, String]] = ProcessManagementClient.getOrCreate(oxygenConfig).getExternalActions(fileId)
    val exp = acceptExp.replace("\"", "")
    val str = extValue match {
      case Some(value) => if (value.contains(exp)) "\"" + value(exp) + "\"" else "\"\""
      case None => "\"\""
    }
    str
  }
  }

  def headerErrorIn: Parser[B] = "headerErrorIn(" ~ stringLiteral ~ ")" ^^ { case "headerErrorIn(" ~ str ~ ")" => {
    val errorList = str.replaceAll("\"","").split(",")
    statsRDD.createOrReplaceTempView("dups")
    val retval = sqlCtx.sql("select headerErrors from dups").rdd.first.toString()
    errorList.forall(retval.contains(_))
  }
  }

  def headerErrorNotIn: Parser[B] = "headerErrorNotIn(" ~ stringLiteral ~ ")" ^^ { case "headerErrorNotIn(" ~ str ~ ")" => {
    val errorList = str.replaceAll("\"","").split(",")
    statsRDD.createOrReplaceTempView("dups")
    val retval = sqlCtx.sql("select headerErrors from dups").rdd.first.toString()
    errorList.forall(!retval.contains(_))
  }
  }

  def footerErrorIn: Parser[B] = "footerErrorIn(" ~ stringLiteral ~ ")" ^^ { case "footerErrorIn(" ~ str ~ ")" => {
    val errorList = str.replaceAll("\"","").split(",")
    statsRDD.createOrReplaceTempView("dups")
    val retval = sqlCtx.sql("select footerErrors from dups").rdd.first.toString()
    errorList.forall(retval.contains(_))
  }
  }

  def footerErrorNotIn: Parser[B] = "footerErrorNotIn(" ~ stringLiteral ~ ")" ^^ { case "footerErrorNotIn(" ~ str ~ ")" => {
    val errorList = str.replaceAll("\"","").split(",")
    statsRDD.createOrReplaceTempView("dups")
    val retval = sqlCtx.sql("select footerErrors from dups").rdd.first.toString()
    errorList.forall(!retval.contains(_))
  }
  }

  def errorIn: Parser[B] = "errorIn(" ~ stringLiteral ~ ")" ^^ { case "errorIn(" ~ str ~ ")" => {
    val errorList = str.replaceAll("\"","").split(",")
    statsRDD.createOrReplaceTempView("dups")
    val retval = sqlCtx.sql("select errorCodes from dups").rdd.first.toString()
    errorList.forall(retval.contains(_))
  }
  }

  def errorNotIn: Parser[B] = "errorNotIn(" ~ stringLiteral ~ ")" ^^ { case "errorNotIn(" ~ str ~ ")" => {
    val errorList = str.replaceAll("\"","").split(",")
    statsRDD.createOrReplaceTempView("dups")
    val retval = sqlCtx.sql("select errorCodes from dups").rdd.first.toString()
    errorList.forall(!retval.contains(_))
  }
  }

  def isEmptyBodyFile: Parser[B] = "isEmptyBodyFile"  ^^ { case "isEmptyBodyFile" => {

    val resultBoolean = statsRDD.select("eventType").first().getLong(0) == -1005L
    resultBoolean
  }
  }

  // override def stringLiteral: Parser[S] =  externalAction | super.stringLiteral

  def stringAction: Parser[S] =   externalAction | stringLiteral

  override def boolStrEqExpr: Parser[B] = headerErrorIn | headerErrorNotIn | footerErrorIn | footerErrorNotIn | errorIn | errorNotIn | stringAction ~ eq ^^ { case a ~ b => b(a) } | isEmptyBodyFile

  override def boolStrNeExpr: Parser[B] = headerErrorIn | headerErrorNotIn | footerErrorIn | footerErrorNotIn | errorIn | errorNotIn | stringAction ~ ne ^^ { case a ~ b => b(a) } | isEmptyBodyFile

}
