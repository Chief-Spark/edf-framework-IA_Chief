package com.experian.edf.oxygen.acceptance.util.dsl

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.domain.internal.events.ValidatedEvent
//import com.experian.edf.oxygen.domain.dsl.events.ValidatedEvent
import com.experian.edf.oxygen.utils.QualityScoreParser

/**
 * The Validated Event Parser uses a Validated object and parses the error codes
 * and quality scores for acceptance / rejection criteria
 *
 * @param validatedEvent : ValidatedEvent that will be used against the expression
 *
 */
class ValidatedEventParser(val validatedEvent: ValidatedEvent) extends QualityScoreParser {

  type S = String
  type LS = List[String]

  def boolErrorCodesIn: Parser[B] = "errorcodeIn(" ~ stringLiteral ~ ")" ^^ { case "errorcodeIn(" ~ errorValue ~ ")" => {
    val errorList = errorValue.replaceAll("\"", "").split(",")
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorCodeValue)
    !(errorList intersect eventErrors).isEmpty
  }
  }

  def boolErrorCodesNotIn: Parser[B] = "errorcodeNotIn(" ~ stringLiteral ~ ")" ^^ { case "errorcodeNotIn(" ~ errorValue ~ ")" => {
    val errorList = errorValue.replaceAll("\"", "").split(",")
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorCodeValue)
    (errorList intersect eventErrors).isEmpty
  }
  }

  def count: Parser[D] = "count(errors)" ^^ { case "count(errors)" => {
    validatedEvent.validation.eventErrors.length.toDouble
  }
  }

  def errorCodelistIn: Parser[B] = "errorcodeInList(" ~ stringListExpr ~ ")" ^^ { case "errorcodeInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorCodeValue)
    !(errorList intersect eventErrors).isEmpty
  }
  }

  def errorCodelistNotIn: Parser[B] = "errorcodeNotInList(" ~ stringListExpr ~ ")" ^^ { case "errorcodeNotInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorCodeValue)
    (errorList intersect eventErrors).isEmpty
  }
  }

  def errorLevelListIn: Parser[B] = "errorlevelInList(" ~ stringListExpr ~ ")" ^^ { case "errorlevelInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorLevel)
    (errorList intersect eventErrors).isEmpty
  }
  }

  def errorLevelListNotIn: Parser[B] = "errorlevelNotInList(" ~ stringListExpr ~ ")" ^^ { case "errorlevelNotInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorLevel)
    (errorList intersect eventErrors).isEmpty
  }
  }

  def errorLevelIn: Parser[B] = "errorlevelIn(" ~ stringListExpr ~ ")" ^^ { case "errorlevelIn(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val errorList = value.replaceAll("\"", "").split(",")
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorLevel)
    !(errorList intersect eventErrors).isEmpty
  }
  }

  def errorLevelNotIn: Parser[B] = "errorlevelNotIn(" ~ stringListExpr ~ ")" ^^ { case "errorlevelNotIn(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val errorList = value.replaceAll("\"", "").split(",")
    val eventErrors = validatedEvent.validation.eventErrors.map(eventError => eventError.errorCodeValue)
    (errorList intersect eventErrors).isEmpty
  }
  }

  def recordExpr: Parser[B] = boolErrorCodesNotIn | boolErrorCodesIn | errorCodelistIn | errorCodelistNotIn | errorLevelListIn | errorLevelListNotIn | errorLevelIn | errorLevelNotIn

  override def boolTerm: Parser[B] = recordExpr | super.boolTerm

  override def arithFactor: Parser[D] = fpn | count | "(" ~> arithExpr <~ ")"

  // order must be maintained
  def stringListExpr: Parser[S] = stringList | stringCommaExpr | stringLiteral

  def stringList: Parser[S] = stringCommaExpr ~ rep(commaString) ^^ { case a ~ b => (a /: b) ((acc, f) => f(acc)) }

  def stringCommaExpr: Parser[S] = stringLiteral ~ commaString ^^ { case a ~ b => b(a) }

  def commaString: Parser[S => S] = "," ~ stringLiteral ^^ { case "," ~ b => _ + "," + b }

  // override def anyExpr: Parser[A] = testString | super.anyExpr

  override def parseAll[T](p: Parser[T], in: CharSequence): ParseResult[T] = {
    val exp = in.toString
    val result = {
        val qualityScoreExp = validatedEvent.validation.qualityScores.foldLeft(exp) { case (s, qs) => s.replaceAll(qs.name, qs.score.toString) }
        super.parseAll(p, qualityScoreExp)
    }
    result match {
      case Success(out, _) => result
      case failure: NoSuccess => throw new OxygenException(failure.msg + ": " + "unable to parse record expression")
    }

  }
}

object ValidatedEventParser {

  def apply(validatedEvent: ValidatedEvent) = {
    new ValidatedEventParser(validatedEvent)
  }


}