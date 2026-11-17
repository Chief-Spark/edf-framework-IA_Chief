package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.events.ValidatedEvent
import com.experian.edf.oxygen.utils.QualityScoreParser
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

case class Stat(qs: Map[String, Double], err: Map[String, String])


object AggregatesParserDF {
  @transient lazy val logger = getLogger(this.getClass)

  def createQSDataFrame(sparkSession: SparkSession, statsDFAll: Dataset[TaggedValidatedEvent]): DataFrame = {

    import sparkSession.implicits._

    val qualityScoreArrayToMap = udf[Map[String, Double], Seq[Row]] { array => array.map { case Row(name: String, score: Double) => (name, score) }.toMap }
    val errorCodesArrayToMap = udf[Map[String, String], Seq[Row]] { array => array.map { case Row(errorCodeName: String, errorCodeValue: String, errorLevel: String, errorType: String, messageKey: String) => (errorCodeName, errorCodeValue) }.toMap }
    val statsDF = statsDFAll.withColumn("qs", qualityScoreArrayToMap($"qualityScores"))
      .withColumn("err", errorCodesArrayToMap($"errorCodes")).cache()
    statsDF
  }

  def createQSDataFrameVE(sparkSession: SparkSession, statsDFAll: Dataset[ValidatedEvent]): DataFrame = {

    import sparkSession.implicits._

    val qualityScoreArrayToMap = udf[Map[String, Double], Seq[Row]] { array => array.map { case Row(name: String, score: Double) => (name, score) }.toMap }
    val errorCodesArrayToMap = udf[Map[String, String], Seq[Row]] { array => array.map { case Row(errorCodeName: String, errorCodeValue: String, errorLevel: String, errorType: String, messageKey: String) => (errorCodeName, errorCodeValue) }.toMap }
    val statsDF = statsDFAll.withColumn("qs", qualityScoreArrayToMap($"validation.qualityScores"))
      .withColumn("err", errorCodesArrayToMap($"validation.eventErrors")).cache()
    statsDF
  }

  def destroyQSDataFrame(statsRDD: DataFrame) = {
    statsRDD.unpersist
  }
}


object AggregatesParserDS {
  @transient lazy val logger = getLogger(this.getClass)

  def createStatsDataset(sparkSession: SparkSession, tce: Dataset[TaggedValidatedEvent]): Dataset[Stat] = {
    import sparkSession.implicits._
    val stats = tce.rdd.map(x => {
      Stat(x.qualityScores.map(y => (y.name, y.score)).toMap,
        x.errorCodes.map(y => (y.errorCodeName, y.errorCodeValue)).toMap)
    })
    stats.toDS
  }

  def createStatsDatasetVE(sparkSession: SparkSession, validatedEvents: Dataset[ValidatedEvent]): Dataset[Stat] = {
    import sparkSession.implicits._
    val stats = validatedEvents.rdd.map(x => {
      Stat(x.validation.qualityScores.map(y => (y.name, y.score)).toMap,
        x.validation.eventErrors.map(y => (y.errorCodeName, y.errorCodeValue)).toMap)
    })
    stats.toDS
  }
}

class AggregatesParserDS(sqlCtx: SQLContext, stats: Dataset[Stat]) extends QualityScoreParser {

  @transient lazy val logger: Logger = getLogger(this.getClass)

  type S = String
  override type D = Double
  override type A = Any
  type DF = DataFrame
  type LSS = List[(String, String)]
  type MSS = Map[String, String]

  def countall: Parser[D] = "count(*)" ^^ { case "count(*)" => stats.count.toDouble }

  def sum: Parser[D] = "sum(" ~ stringLiteral ~ ")" ^^ { case "sum(" ~ fieldName ~ ")" => {
    stats.agg(fieldName.replaceAll("\"", "") -> "sum").first.getDouble(0)
  }
  }

  def count: Parser[D] = "count(" ~ stringLiteral ~ ")" ^^ { case "count(" ~ fieldName ~ ")" => {
    stats.agg(fieldName.replaceAll("\"", "") -> "count").first.getLong(0).toDouble
  }
  }

  def average: Parser[D] = "average(" ~ stringLiteral ~ ")" ^^ { case "average(" ~ fieldName ~ ")" => {
    stats.agg(fieldName.replaceAll("\"", "") -> "avg").first.getDouble(0)
  }
  }

  def stddev: Parser[D] = "stddev(" ~ stringLiteral ~ ")" ^^ { case "stddev(" ~ fieldName ~ ")" => {
    stats.agg(fieldName.replaceAll("\"", "") -> "stddev").first.getDouble(0)
  }
  }

  def min: Parser[D] = "min(" ~ stringLiteral ~ ")" ^^ { case "min(" ~ fieldName ~ ")" => {
    stats.agg(fieldName.replaceAll("\"", "") -> "min").first.getDouble(0)
  }
  }

  def max: Parser[D] = "max(" ~ stringLiteral ~ ")" ^^ { case "max(" ~ fieldName ~ ")" => {
    stats.agg(fieldName.replaceAll("\"", "") -> "max").first.getDouble(0)
  }
  }

  def duplicates: Parser[LSS] = "duplicates(" ~ stringLiteral ~ ")" ^^ { case "duplicates(" ~ field ~ ")" => {
    stats.createOrReplaceTempView("dups")
    val fieldName = field.replaceAll("\"", "")
    val retval = sqlCtx.sql("select " + fieldName + ", count(*) as countval from dups group by " + fieldName)
    retval.rdd.collect.map(row => (row(0).toString, row(1).toString)).toList
  }
  }

  def aggValidation: Parser[S] = "(if(" ~ boolExpr ~ ") warning " ~ stringLiteral ~ ")" ^^ { case "(if(" ~ bVal ~ ") warning " ~ eVal ~ ")" => {
    if (bVal) eVal else ""
  }
  }

  def stringListExpr: Parser[S] = stringList | stringCommaExpr | stringLiteral

  def stringList: Parser[S] = stringCommaExpr ~ rep(commaString) ^^ { case a ~ b => (a /: b) ((acc, f) => f(acc)) }

  def stringCommaExpr: Parser[S] = stringLiteral ~ commaString ^^ { case a ~ b => b(a) }

  def commaString: Parser[S => S] = "," ~ stringLiteral ^^ { case "," ~ b => _ + "," + b }

  def countByField: Parser[LSS] = "countByField(" ~ stringListExpr ~ ")" ^^ { case "countByField(" ~ field ~ ")" => {
    stats.createOrReplaceTempView("dups")
    val fieldName = field.replaceAll("\"", "")
    val retval = sqlCtx.sql("select " + fieldName + ", count(*) as countval from dups group by " + fieldName)
    retval.rdd.collect.map { row => (row(0).toString, row(1).toString) }.toList
  }
  }

  def countByFields: Parser[MSS] = "countByFields(" ~ stringListExpr ~ ")" ^^ { case "countByFields(" ~ field ~ ")" => {
    var valMap = Map[String, String]()

    stats.createOrReplaceTempView("dups")
    val fieldNames = field.replaceAll("\"", "").split(",").toList
    val fieldNamesStr = fieldNames.map(field => "count(" + field + ")").mkString(",")
    val retval = sqlCtx.sql("select " + fieldNamesStr + "from dups")
    val valueList = retval.rdd.collect().toList(0).mkString(",").split(",")
    val valueMap = fieldNames.map(row => "count(\"" + row + "\")") zip valueList
    valueMap.foreach(x => valMap += (x._1 -> x._2))
    valMap
  }
  }

  def sumByFields: Parser[MSS] = "sumByFields(" ~ stringListExpr ~ ")" ^^ { case "sumByFields(" ~ field ~ ")" => {
    var valMap = Map[String, String]()

    stats.createOrReplaceTempView("dups")
    val fieldNames = field.replaceAll("\"", "").split(",").toList
    val fieldNamesStr = fieldNames.map(field => "sum(" + field + ")").mkString(",")
    val retval = sqlCtx.sql("select " + fieldNamesStr + "from dups")
    val valueList = retval.rdd.collect().toList(0).mkString(",").split(",")
    val valueMap = fieldNames.map(row => "sum(\"" + row + "\")") zip valueList
    valueMap.foreach(x => valMap += (x._1 -> x._2))
    valMap
  }
  }

  def aggExpr: Parser[A] = countall | sum | count | average | duplicates | min | max | aggValidation | countByField | sumByFields | countByFields

  override def anyExpr: Parser[A] = arithExpr | stringLiteral | aggExpr

  override def arithFactor: Parser[D] = fpn | countall | sum | count | average | min | max | "(" ~> arithExpr <~ ")"

  override def arithExpr: Parser[D] = super.arithExpr | sum | count | average | min | max

}

class AggregatesParserDF(sqlCtx: SQLContext, statsRDD: DataFrame) extends QualityScoreParser {

  @transient lazy val logger: Logger = getLogger(this.getClass)

  type S = String
  override type D = Double
  override type A = Any
  type DF = DataFrame
  type LSS = List[(String, String)]
  type MSS = Map[String, String]

  def countall: Parser[D] = "count(*)" ^^ { case "count(*)" => statsRDD.count.toDouble }

  def sum: Parser[D] = "sum(" ~ stringLiteral ~ ")" ^^ { case "sum(" ~ fieldName ~ ")" => {
    statsRDD.agg(fieldName.replaceAll("\"", "") -> "sum").first.getDouble(0)
  }
  }

  def average: Parser[D] = "average(" ~ stringLiteral ~ ")" ^^ { case "average(" ~ fieldName ~ ")" => {
    statsRDD.agg(fieldName.replaceAll("\"", "") -> "avg").first.getDouble(0)
  }
  }

  def stddev: Parser[D] = "stddev(" ~ stringLiteral ~ ")" ^^ { case "stddev(" ~ fieldName ~ ")" => {
    statsRDD.agg(fieldName.replaceAll("\"", "") -> "stddev").first.getDouble(0)
  }
  }

  def min: Parser[D] = "min(" ~ stringLiteral ~ ")" ^^ { case "min(" ~ fieldName ~ ")" => {
    statsRDD.agg(fieldName.replaceAll("\"", "") -> "min").first.getDouble(0)
  }
  }

  def max: Parser[D] = "max(" ~ stringLiteral ~ ")" ^^ { case "max(" ~ fieldName ~ ")" => {
    statsRDD.agg(fieldName.replaceAll("\"", "") -> "max").first.getDouble(0)
  }
  }

  def duplicates: Parser[LSS] = "duplicates(" ~ stringLiteral ~ ")" ^^ { case "duplicates(" ~ field ~ ")" => {
    statsRDD.createOrReplaceTempView("dups")
    val fieldName = field.replaceAll("\"", "")
    val retval = sqlCtx.sql("select " + fieldName + ", count(*) as countval from dups group by " + fieldName)
    retval.rdd.collect.map(row => (row(0).toString, row(1).toString)).toList
  }
  }

  def aggValidation: Parser[S] = "(if(" ~ boolExpr ~ ") warning " ~ stringLiteral ~ ")" ^^ { case "(if(" ~ bVal ~ ") warning " ~ eVal ~ ")" => {
    if (bVal) eVal else ""
  }
  }

  def stringListExpr: Parser[S] = stringList | stringCommaExpr | stringLiteral

  def stringList: Parser[S] = stringCommaExpr ~ rep(commaString) ^^ { case a ~ b => (a /: b) ((acc, f) => f(acc)) }

  def stringCommaExpr: Parser[S] = stringLiteral ~ commaString ^^ { case a ~ b => b(a) }

  def commaString: Parser[S => S] = "," ~ stringLiteral ^^ { case "," ~ b => _ + "," + b }

  def countByField: Parser[LSS] = "countByField(" ~ stringListExpr ~ ")" ^^ { case "countByField(" ~ field ~ ")" => {
    statsRDD.createOrReplaceTempView("dups")
    val fieldName = field.replaceAll("\"", "")
    val retval = sqlCtx.sql("select " + fieldName + ", count(*) as countval from dups group by " + fieldName)
    retval.rdd.collect.map { row => (row(0).toString, row(1).toString) }.toList

  }
  }

  def sumByFields: Parser[MSS] = "sumByFields(" ~ stringListExpr ~ ")" ^^ { case "sumByFields(" ~ field ~ ")" => {
    var valMap = Map[String, String]()

    statsRDD.createOrReplaceTempView("dups")
    val fieldNames = field.replaceAll("\"", "").split(",").toList
    val fieldNamesStr = fieldNames.map(field => "sum(" + field + ")").mkString(",")
    val retval = sqlCtx.sql("select " + fieldNamesStr + "from dups")
    val valueList = retval.rdd.collect().toList(0).mkString(",").split(",")
    val valueMap = fieldNames.map(row => "sum(\"" + row + "\")") zip valueList
    valueMap.foreach(x => valMap += (x._1 -> x._2))
    valMap
  }
  }

  def aggExpr: Parser[A] = countall | sum | average | duplicates | min | max | aggValidation | countByField | sumByFields

  override def anyExpr: Parser[A] = arithExpr | stringLiteral | aggExpr

  override def arithFactor: Parser[D] = fpn | countall | sum | average | min | max | "(" ~> arithExpr <~ ")"

  override def arithExpr: Parser[D] = super.arithExpr | sum | average | min | max

}
