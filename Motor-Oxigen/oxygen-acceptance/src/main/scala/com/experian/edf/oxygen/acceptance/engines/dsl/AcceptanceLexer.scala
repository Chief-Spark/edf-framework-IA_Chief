package com.experian.edf.oxygen.acceptance.engines.dsl

import org.slf4j.LoggerFactory.getLogger

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers

/**
 * A basic tokenizer/lexer for Acceptance statements.
 *
 */
object AcceptanceLexer extends RegexParsers {
  @transient private lazy val logger = getLogger(this.getClass)

  override def skipWhitespace: Boolean = true
  override val whiteSpace: Regex = "[ \t\r\f]+".r

  def identifierString: Parser[String] = """[a-zA-Z_][a-zA-Z0-9_]*""".r ^^ { _.toString }

  def identifierList: Parser[List[String]] = rep1sep(identifierString, ",")

  def numberString: Parser[Int] = """(0|[1-9]\d*)""".r ^^ { _.toInt }

  def numberList: Parser[List[Int]] = rep1sep(numberString, ",")

  def anyOf: Parser[ANYOF] = "anyOf" ~ "(" ~ identifierList ~ ")" ^^ {
    case _ ~ _ ~ idList ~ _ => ANYOF(idList)
  }

  def allOf: Parser[ALLOF] = "allOf" ~ "(" ~ identifierList ~ ")" ^^ {
    case _ ~ _ ~ idList ~ _ => ALLOF(idList)
  }

  def hasErrorlevelIn: Parser[HASERRORLEVELIN] = "hasErrorlevelIn" ~ "(" ~ numberList ~ ")" ^^ {
    case _ ~ _ ~ valList ~ _ => HASERRORLEVELIN(valList)
  }

  def hasErrorlevelNotIn: Parser[HASERRORLEVELNOTIN] = "hasErrorlevelNotIn" ~ "(" ~ numberList ~ ")" ^^ {
    case _ ~ _ ~ valList ~ _ => HASERRORLEVELNOTIN(valList)
  }

  def tokenizer: Parser[List[AcceptanceToken]] = {
    phrase((anyOf | allOf) ~ opt(hasErrorlevelIn | hasErrorlevelNotIn)) ^^ {
      case anyof ~ Some(a: AcceptanceToken) => List(anyof, a)
      case anyof ~ None => List(anyof)
      case allof ~ Some(a: AcceptanceToken) => List(allof, a)
      case allof ~ None => List(allof)
    }
  }

  def apply(toParse: String): Option[List[AcceptanceToken]] = {
    parse(tokenizer, toParse) match {
      case NoSuccess(errorMsg, nextToken) =>
        logger.debug(s"Error found at line: ${nextToken.pos.line} column ${nextToken.pos.column}. $errorMsg")
        Option.empty
      case Success(result, _) => Some(result)
    }
  }
}
