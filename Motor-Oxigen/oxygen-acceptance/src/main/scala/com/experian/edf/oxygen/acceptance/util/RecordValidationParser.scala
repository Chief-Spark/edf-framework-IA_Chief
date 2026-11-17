package com.experian.edf.oxygen.acceptance.util

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.domain.internal.TaggedValidatedEvent
import com.experian.edf.oxygen.validator.ValidationResult


/**
 * The Record Level Parser uses a Either of TaggedValidatedEvent or ValidatedResult object and parses the error codes
 * and quality scores for acceptance / rejection criteria
 *
 * @param validatedObject : TaggedValidatedEvent or ValidatedResult object
 *
 */
class RecordValidationParser(val validatedObject: Either[TaggedValidatedEvent, ValidationResult]) extends QualityScoreParser {

  type S = String
  type LS = List[String]

  def boolErrorCodesIn: Parser[B] = "errorcodeIn(" ~ stringLiteral ~ ")" ^^ { case "errorcodeIn(" ~ errorValue ~ ")" => {
    val errorList = errorValue.replaceAll("\"", "").split(",")
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorCodeValue).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorCodeValue).mkString(",")
    }
    errorList.forall(errorString.contains(_))
  }
  }

  def boolErrorCodesNotIn: Parser[B] = "errorcodeNotIn(" ~ stringLiteral ~ ")" ^^ { case "errorcodeNotIn(" ~ errorValue ~ ")" => {
    val errorList = errorValue.replaceAll("\"", "").split(",")
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorCodeValue).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorCodeValue).mkString(",")
    }
    errorList.forall(!errorString.contains(_))
  }
  }

  def count: Parser[D] = "count(errors)" ^^ { case "count(errors)" => {
    validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.length.toDouble
      case Right(validatedResult) => validatedResult.errors.length.toDouble
    }
  }
  }

  def errrorCodelistIn: Parser[B] = "errorcodeInList(" ~ stringListExpr ~ ")" ^^ { case "errorcodeInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorCodeValue).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorCodeValue).mkString(",")
    }
    errorList.forall(errorString.contains(_))
  }
  }

  def errrorCodelistNotIn: Parser[B] = "errorcodeNotInList(" ~ stringListExpr ~ ")" ^^ { case "errorcodeNotInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorCodeValue).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorCodeValue).mkString(",")
    }
    errorList.forall(!errorString.contains(_))
  }
  }

  def errrorLevelListIn: Parser[B] = "errorlevelInList(" ~ stringListExpr ~ ")" ^^ { case "errorlevelInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorLevel).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorLevel).mkString(",")
    }
    errorList.forall(errorString.contains(_))
  }
  }

  def errrorLevelListNotIn: Parser[B] = "errorlevelNotInList(" ~ stringListExpr ~ ")" ^^ { case "errorlevelNotInList(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"
    val errorList = value.split(regex).map(x => x.replaceAll("\"", ""))
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorLevel).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorLevel).mkString(",")
    }
    errorList.forall(!errorString.contains(_))
  }
  }

  def errrorLevelIn: Parser[B] = "errorlevelIn(" ~ stringListExpr ~ ")" ^^ { case "errorlevelIn(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val errorList = value.replaceAll("\"", "").split(",")
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorLevel).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorLevel).mkString(",")
    }
    errorList.forall(errorString.contains(_))
  }
  }

  def errrorLevelNotIn: Parser[B] = "errorlevelNotIn(" ~ stringListExpr ~ ")" ^^ { case "errorlevelNotIn(" ~ value ~ ")" => {
    // split on the comma only if that comma has zero, or an even number of quotes ahead of it.
    val errorValue = value.replaceAll("\"", "").split(",")
    val errorString = validatedObject match {
      case Left(validatedRecord) => validatedRecord.errorCodes.map(x => x.errorLevel).mkString(",")
      case Right(validatedResult) => validatedResult.errors.map(x => x.errorLevel).mkString(",")
    }
    errorValue.forall(!errorString.contains(_))
  }
  }


  // TODO: The above functions would fail if string has a comma

  def recordExpr: Parser[B] = boolErrorCodesNotIn | boolErrorCodesIn | errrorCodelistIn | errrorCodelistNotIn | errrorLevelListIn | errrorLevelListNotIn | errrorLevelIn | errrorLevelNotIn

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


    val result = validatedObject match {
      case Left(validatedRecord) => {
        val qualityScoreExp = validatedRecord.qualityScores.foldLeft(exp) { case (s, qs) => s.replaceAll(qs.name, qs.score.toString) }
        super.parseAll(p, qualityScoreExp)
      }
      case Right(_) => super.parseAll(p, exp)
    }
    // println(result)
    result match {
      case Success(out, _) => result
      case failure: NoSuccess => throw new OxygenException(failure.msg + ": " + "unable to parse record expression")
      // TODO: throw oxygen exception or deal with failure scenarioes
    }

  }
}

object RecordValidationParser {

  def apply(validatedRecord: Either[TaggedValidatedEvent, ValidationResult]) = {
    new RecordValidationParser(validatedRecord)
  }


}