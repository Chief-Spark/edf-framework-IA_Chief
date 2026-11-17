package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import scala.util.parsing.combinator.JavaTokenParsers

class BaseConsistencyParser extends JavaTokenParsers with Serializable {
  def booleanValue: Parser[Boolean] = "true" ^^ (_ => true) | "false" ^^ (_ => false)
  def value: Parser[Any] = stringLiteral ^^ (_.replaceAll("\"", "")) | floatingPointNumber ^^ (_.toDouble) | booleanValue
  def valueSequence: Parser[List[Any]] = repsep(value, ",")
  def stringVar: Parser[String] = "[a-z,A-Z,0-9,_]+".r
  def datePattern: Parser[String] = "[a-z,A-Z,0-9,_,\\-\\,/,.]+".r
  def dictionaryTerm: Parser[(String, String)] = "$" ~ stringVar ^^ {x =>
    ("", x._2.replaceAll("\"", ""))
  } | stringVar  ~ ".$" ~ stringVar ^^ { x =>
    val domain = x._1._1
    val term = x._2
    (domain, term)
  }

  def dateFormat: Parser[String] = ".asDateFormat" ~ "(" ~ ")"  ^^ { x => x._1._1} | ".asDateFormat" | ".asTimestampFromString" ~ "(" ~ ")"  ^^ { x => x._1._1} | ".asTimestampFromString"
  def dictionaryTermWithType: Parser[(String, String, String, Option[String])] = dictionaryTerm ~ (".asDateFormat" | ".asTimestampFromString") ~ "(" ~ datePattern ~ ")" ^^ { x =>
    val domain = x._1._1._1._1._1
    val term = x._1._1._1._1._2
    val asType = x._1._1._1._2.drop(1)
    val pattern = x._1._2
    (domain, term, asType, Some(pattern))
  } | dictionaryTerm ~ (".asJsonArray"| ".asJsonObject"| ".asString" | ".asInt" | ".asDouble" | ".asBoolean" | ".asLong" | ".asPeriod" | dateFormat)  ^^ { x =>
  val domain = x._1._1
  val term = x._1._2
  val asType = x._2.drop(1)
  (domain, term, asType, None)
}

  // The order of alternatives is important.  It is evaluated left to right
  def comparator: Parser[String] =  "==" | "!=" | ">=" | "<=" | "<" | ">"
  def operator: Parser[String] = "+" | "*" | "-" | "/"
  def qual: Parser[String] = "this." ^^ (x => "this") | "that." ^^ (x => "that")
  def expression: Parser[(String, (String, String, String, Option[String]))] = qual ~ dictionaryTermWithType ^^ (x => (x._1, x._2))
}
