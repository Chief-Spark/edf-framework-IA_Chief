package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.Util

/**
 * OF-600 Feature to support lookup for error level (error severity) using errorcode as key in consistencyInfo.
 * Traditionally all the expressions in the consistencyCheck fields return boolean values (true/false).
 * Unlike other fields, consistency error level field should return a string value instead of boolean.
 *
 * Supported expressions:
 * 1. first.external.<tablename>.filter(<fieldname>==<key>).select(<valueField>) -- returns first (or only) value as a string from the list
 * 2. all.external.<tablename>.filter(<fieldname>==<key>).select(<valueField>) -- return all the values as comma seperated string
 * 3. Same input string is returned in case expression does not match the above two formats
 * Example:
 * "consistencyInfo": {
 * "code": "errorCode6",
 * "subCode": "",
 * "level": "first.external.consistencyfiltertable1.filter(errorcode == errorCode6).select(errorvalue)"
 * }
 *
 * @param lookupService
 */
class ConsistencyInfoParser(lookupService: OxygenLookup) extends BaseConsistencyParser with Serializable {

  def applyFilter: Parser[String] = firstExternalValueExpression | "(" ~> firstExternalValueExpression <~ ")" | AllExternalValueExpression | "(" ~> AllExternalValueExpression <~ ")" | returnString

  def returnString: Parser[String] = stringVar ^^ { x => x }

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

  def firstExternalValueExpression: Parser[String] = "first." ~ externalExpression ~ asType  ^^ { x =>
    val tableName = x._1._2._1
    val fieldMap = x._1._2._2
    val checkFieldName = x._1._2._3
    val lhsValue = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService).head
    lhsValue

  }


  def AllExternalValueExpression: Parser[String] = "all." ~ externalExpression ~ asType  ^^ { x =>
    val tableName = x._1._2._1
    val fieldMap = x._1._2._2
    val checkFieldName = x._1._2._3
    val lhsValue = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService)
    lhsValue.mkString(",")

  }
}

