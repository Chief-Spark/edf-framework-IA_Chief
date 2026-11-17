package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.{ExpressionConstant, Util}
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class ShouldFilterEventParser(lookupService: OxygenLookup) extends BaseConsistencyParser with Serializable {
  def or: Parser[Boolean => Boolean] = "||" ~ booleanExpression ^^ { case "||" ~ b => _ || b }

  def and: Parser[Boolean => Boolean] = "&&" ~ booleanExpression ^^ { case "&&" ~ b => _ && b }

  def applyFilter: Parser[Boolean] = booleanExpressionSequence | "(" ~> booleanExpressionSequence <~ ")"

  def booleanExpressionSequence: Parser[Boolean] = booleanExpression ~ rep(or | and) ^^ { case a ~ b => (a /: b) ((acc, f) => f(acc)) }

  def filterExpressionList: Parser[Map[String, String]] = repsep(filterExpression, ",") ^^ { x => x.reduce(_ ++ _) }

  def filterExpression: Parser[Map[String, String]] = stringVar ~ "==" ~ stringVar ^^ { x =>
    val tableFieldName = x._1._1
    val thatEventValue = x._2
    Map(tableFieldName -> thatEventValue)
  }

  def select: Parser[String] = ".select" ~ "(" ~ stringVar ~ ")" ^^ { x => x._1._2 }

  def externalExpression: Parser[(String, Map[String, String], String)] = "external." ~ stringVar ~ ".filter" ~ "(" ~ filterExpressionList ~ ")" ~ select ^^ { x =>
    val tableName = x._1._1._1._1._1._2
    val filterMap = x._1._1._2
    val checkFieldName = x._2
    (tableName, filterMap, checkFieldName)
  }

  def asType: Parser[Option[String]] = opt(".asString" | ".asInt" | ".asBoolean" | ".asDouble" | ".asLong" | ".asPeriod" | ".asTimestampFromString")

  def booleanExpression: Parser[Boolean] = "first." ~ externalExpression ~ asType ~ comparator ~ value ^^ { x =>
    val tableName = x._1._1._1._2._1
    val fieldMap = x._1._1._1._2._2
    val checkFieldName = x._1._1._1._2._3
    val asType = x._1._1._2.getOrElse(".asString").drop(1)
    val lhsValue = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService).head
    val cond = x._1._2
    val rhsValue = x._2
    // val boolValue2 = Util.compareValues(lhsValue, rhsValue, cond, asType)
    // TODO: if lhsvalue is empty, to conversions would throw exception
    val boolValue = asType match {
      case ExpressionConstant.AsInt => Util.compareInt(lhsValue.toInt, rhsValue.asInstanceOf[Double].toInt, cond)
      case ExpressionConstant.AsString => Util.compareString(lhsValue, rhsValue.toString, cond)
      case ExpressionConstant.AsDouble => Util.compareDouble(lhsValue.toDouble, rhsValue.asInstanceOf[Double], cond)
      case ExpressionConstant.AsBoolean => Util.compareBoolean(lhsValue.toBoolean, rhsValue.asInstanceOf[Boolean], cond)
      case ExpressionConstant.AsLong => Util.compareLong(lhsValue.toLong, rhsValue.asInstanceOf[Double].toLong, cond)
      case ExpressionConstant.AsJsonArray => throw new NotImplementedException
      case ExpressionConstant.AsJsonObject => throw new NotImplementedException
      case _ => throw new NotImplementedException
    }
    boolValue
  } | value ~ ("in" | "notIn") ~ externalExpression ^^ { x =>
    val lhsValue = x._1._1.toString()
    val cond = x._1._2
    val tableName = x._2._1
    val fieldMap = x._2._2
    val checkFieldName = x._2._3
    val rhsValue = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService)
    if (cond == "in")
      rhsValue.contains(lhsValue)
    else
      !rhsValue.contains(lhsValue)
  } | booleanValue | "(" ~> booleanExpression <~ ")" | "(" ~> booleanExpressionSequence <~ ")"

}

