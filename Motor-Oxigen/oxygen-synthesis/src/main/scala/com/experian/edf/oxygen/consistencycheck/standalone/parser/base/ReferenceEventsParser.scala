package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.{ExpressionConstant, Util}

class ReferenceEventsParser(thatEvent: ConsistencyParserEvents, thisEvent: ConsistencyParserEvents, currentDateTime: Long) extends BaseParser with Serializable {

  def or: Parser[Boolean => Boolean] = "||" ~ booleanExpression ^^ { case "||" ~ b => _ || b }

  def and: Parser[Boolean => Boolean] = "&&" ~ booleanExpression ^^ { case "&&" ~ b => _ && b }

  def applyScope: Parser[Boolean] = booleanExpressionSequence | "(" ~> booleanExpressionSequence <~ ")"

  def booleanExpressionSequence: Parser[Boolean] = booleanExpression ~ rep(or | and) ^^ { case a ~ b => (a /: b) ((acc, f) => f(acc)) }

  def currentDateTimeWithType: Parser[(Any, String)]  = "env." ~ "CURRENT_DATE_TIME" ~ ( ".asPeriod" | ".asLong" | ".asDateFormat") ~ opt("(") ~ opt(datePattern) ~ opt(")") ^^ { x =>
    val asType = x._1._1._1._2.drop(1)
    val pattern = x._1._2
    (Util.getCurrentDateTimeAsType(currentDateTime, asType, pattern), asType)
  }

  def currentDateTimeWithOperator: Parser[Any] = currentDateTimeWithType ~ ("+"|"-") ~ value ^^ {x =>
    val currentValue = x._1._1._1
    val asType = x._1._1._2
    val op = x._1._2
    val thatValue = x._2
    Util.applyOperation(op, asType, currentValue, thatValue)

  }

  def currentDateTimeExpression: Parser[Any] = currentDateTimeWithOperator | currentDateTimeWithType ^^ { x => x._1}
  // TODO: check opt, should return type as well

  def exprWithOperator: Parser[(String, Any, String)] = expression ~ operator ~ value ^^ { x =>
    val termQual = x._1._1._1
    val termDomain = x._1._1._2._1
    val termName = x._1._1._2._2
    val termAsType = x._1._1._2._3

    val op = x._1._2
    val opValue = x._2

    val exprType = Util.getType(termAsType).tpe
    type termType = exprType.type

    val termValue= if (termQual == ExpressionConstant.That) {
      Util.getValueFromConsistencyParserEvent(thatEvent, termDomain, termName, termAsType, None)
    }
    else
      Util.getValueFromConsistencyParserEvent(thisEvent, termDomain, termName, termAsType, None)

    val exprValue = if (termValue == null)
      null
    else
      Util.applyOperation(op, termAsType, termValue, opValue)

    (termQual, exprValue, termAsType)
    // TODO: .asDateFormat and operations on period support
    // TODO: assigning as a tuple, can it be changed?
    // TODO: try opt

  } | expression ^^ { x =>
    val termQual = x._1
    val termDomain = x._2._1
    val termName = x._2._2
    val termAsType = x._2._3
    val termPattern = x._2._4

    val termValue= if (termQual == ExpressionConstant.That) {
      Util.getValueFromConsistencyParserEvent(thatEvent, termDomain, termName, termAsType, termPattern)
    }
    else
      Util.getValueFromConsistencyParserEvent(thisEvent, termDomain, termName, termAsType, termPattern)

    (termQual,termValue, termAsType)
  }


  // TODO: support arthimetic expression

  def booleanExpression: Parser[Boolean] = exprWithOperator ~ comparator ~ exprWithOperator ^^ { x =>
    val lhsQual = x._1._1._1
    val lhsValue = x._1._1._2
    val lhsType = x._1._1._3

    val cond = x._1._2

    val rhsQual = x._2._1
    val rhsValue = x._2._2
    val rhsType = x._2._3

    if (rhsType != lhsType) throw new Exception("Comparision of incompatible types in expression: " + this.stringLiteral.toString)

    Util.compareValues(lhsValue, rhsValue, cond, rhsType)

  } | exprWithOperator ~ comparator ~ value ^^ { x =>
    val lhsQual = x._1._1._1
    val lhsValue = x._1._1._2
    val lhsType = x._1._1._3

    val cond = x._1._2

    val rhsValue = x._2
    Util.compareValues(lhsValue, rhsValue, cond, lhsType)

  } | exprWithOperator ~ comparator ~ currentDateTimeExpression ^^ { x =>
    val lhsQual = x._1._1._1
    val lhsType = x._1._1._3
    val lhsValue = x._1._1._2

    val cond = x._1._2

    val rhsValue = x._2
    Util.compareValues(lhsValue, rhsValue, cond, lhsType)

  } | "exists(" ~ "that." ~ dictionaryTerm ~ ")" ^^ { x =>
    val termDomain = x._1._2._1
    val termName = x._1._2._2
    Util.doesTermExist(thatEvent, termDomain, termName)
  } | "("~>booleanExpression<~")" | "("~>booleanExpressionSequence<~")"
}
