package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.Util
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ExpressionConstant


class TriggerConditionParser(event: ConsistencyParserEvents) extends BaseConsistencyParser with Serializable {
  def or: Parser[Boolean => Boolean] = "||"~booleanExpression ^^ {case "||"~b => _ || b}
  def and: Parser[Boolean => Boolean] = "&&"~booleanExpression ^^ {case "&&"~b => _ && b}
  def applyTrigger: Parser[Boolean] = booleanExpressionSequence | "("~>booleanExpressionSequence<~")"
  def booleanExpressionSequence: Parser[Boolean] = booleanExpression~rep(or|and) ^^ {case a~b => (a /: b)((acc,f) => f(acc))}
  def booleanExpression: Parser[Boolean] = expression ~ comparator ~ value ^^ { x =>
    val termDomain = x._1._1._2._1
    val termName = x._1._1._2._2
    val termType = x._1._1._2._3
    val termDatePattern = x._1._1._2._4

    val cond = x._1._2

    val lhsValue = Util.getValueFromConsistencyParserEvent(event, termDomain, termName, termType, termDatePattern)
    val rhsValue = x._2
    Util.compareValues(lhsValue, rhsValue, cond, termType)

  } | expression ~ comparator ~ expression ^^ { x =>
    val lhsQual = x._1._1._1
    val rhsQual = x._2._1
    if ((lhsQual != "this") || (rhsQual != "this")) throw new Exception("Comparision is not valid : " + this.stringLiteral.toString)
    val lhsTermDomain = x._1._1._2._1
    val rhsTermDomain = x._2._2._1
    val lhsTermName = x._1._1._2._2
    val rhsTermName = x._2._2._2
    val lhsTermType = x._1._1._2._3
    val rhsTermType = x._2._2._3
    val lhsDatePattern = x._1._1._2._4
    val rhsDatePattern = x._2._2._4

    val cond = x._1._2

    if (rhsTermType != lhsTermType) throw new Exception("Comparision of incompatible types in expression: " + this.stringLiteral.toString)

    val termType = rhsTermType

    val lhsValue = Util.getValueFromConsistencyParserEvent(event, lhsTermDomain, lhsTermName, termType, lhsDatePattern)
    val rhsValue = Util.getValueFromConsistencyParserEvent(event, rhsTermDomain, rhsTermName, termType, rhsDatePattern)
    val c =    Util.compareValues(lhsValue, rhsValue, cond, termType)

    Util.compareValues(lhsValue, rhsValue, cond, termType)

  } | "exists(" ~ "this." ~ dictionaryTerm ~ ")" ^^ { x =>
    val termDomain = x._1._2._1
    val termName = x._1._2._2

    Util.doesTermExist(event, termDomain, termName)

  } | ExpressionConstant.Any ^^ { x =>
    true
  } | "("~>booleanExpression<~")" | "("~>booleanExpressionSequence<~")"
}
