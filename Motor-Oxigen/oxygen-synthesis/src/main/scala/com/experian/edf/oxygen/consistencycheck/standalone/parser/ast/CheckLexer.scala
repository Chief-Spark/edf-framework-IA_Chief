package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import org.slf4j.LoggerFactory.getLogger

import scala.util.parsing.combinator.RegexParsers

/**
 * Step1: Lexical Analysis for the Check Expressions.
 */
object CheckLexer extends RegexParsers {
  override def skipWhitespace = true

  override val whiteSpace = "[ \t\r\f]+".r
  private val stringVar: Parser[String] = "[a-zA-Z0-9_]+".r
  private val floatingPointNumber: Parser[String] =
    """-?(\d+(\.\d*)?|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r
  private val stringLiteral: Parser[String] = """"[^"]*"""".r
  private val value: Parser[Any] = stringLiteral ^^ (_.replaceAll("\"", "")) | floatingPointNumber ^^ (_.toDouble) | trueValue | falseValue
  @transient lazy val logger = getLogger(this.getClass)


  def apply(code: String, checkName: String): Option[List[ConsistencyCheckToken]] = {
    parse(tokens, code) match {
      case NoSuccess(msg, next) => {
        logger.error("Error in lexer for check: " + checkName + " at line: " + next.pos.line + ", column: " + next.pos.column + " - " + msg)
        None
      }
      case Success(result, next) => Some(result)
    }
  }

  // TODO: "inType" takes "in" in anyword..change it
  def tokens: Parser[List[ConsistencyCheckToken]] = {
    phrase(rep1(mostRecentType | firstType | lastType | anyOneType | prevPeriodType | referenceEventCount | willBeMigratedType | checkStateType |
      notInList | inList | inType | notInType |
      external | filter | select | comma |
      thatGlobalIdentifier | thisGlobalIdentifier | thatIdentifier | thisIdentifier |
      dot | leftParenthesis | rightParenthesis | equals | notEqual | greaterOrEqual | greaterThan | lessOrEqual | lessThan | assign |
      trueValue | falseValue | numeric | literal | and | or | identifier)) ^^ { rawTokens =>
      rawTokens
    }
  }


  def identifier: Parser[IDENTIFIER] =
    "[a-zA-Z_0-9]+".r ^^ { str => IDENTIFIER(str) }

  def literal: Parser[LITERAL] = {
    """"[^"]*"""".r ^^ { str =>
      val content = str.substring(1, str.length - 1)
      LITERAL(content)
    }
  }

  def valseq: Parser[List[Any]] = repsep(value, ",")

  def inList: Parser[INLIST] = {
    "in" ~ "(" ~ valseq ~ ")" ^^ { x => INLIST(x._1._2) }
  }

  def notInList: Parser[NOTINLIST] = {
    "notIn" ~ "(" ~ valseq ~ ")" ^^ { x => NOTINLIST(x._1._2) }
  }

  def sequence: Parser[SEQUENCE] = repsep(value, ",") ^^ { valSeq => SEQUENCE(valSeq) }

  def inType: Parser[IN.type] = {
    "in" ^^ (_ => IN)
  }

  def notInType: Parser[NOTIN.type] = {
    "notIn" ^^ (_ => NOTIN)
  }

  //
  def external: Parser[EXTERNALTABLE] = "external." ~ stringVar ~ "." ^^ { str => EXTERNALTABLE(str._1._2) }

  def filter: Parser[FILTER.type] = {
    "filter" ^^ (_ => FILTER)
  }

  def select: Parser[SELECT] = "select" ~ "(" ~ stringVar ~ ")" ^^ { str => SELECT(str._1._2) }


  def mostRecentType: Parser[MOSTRECENT.type] = {
    "mostRecent" ^^ (_ => MOSTRECENT)
  }

  def checkStateType: Parser[CHECKSTATE.type] = {
    SynthesisConstants.SHOULD_ACCEPT_STATE ^^ (_ => CHECKSTATE)
  }

  def firstType: Parser[FIRST.type] = {
    "first" ^^ (_ => FIRST)
  }

  def lastType: Parser[LAST.type] = {
    "last" ^^ (_ => LAST)
  }

  def anyOneType: Parser[ANYONE.type] = {
    "anyOne" ^^ (_ => ANYONE)
  }

  def prevPeriodType: Parser[PREV.type] = {
    "prevPeriod" ^^ (_ => PREV)
  }

  def referenceEventCount: Parser[REFERENCEEVENTCOUNT.type] = {
    "referenceEventCount" ^^ (_ => REFERENCEEVENTCOUNT)
  }

  def willBeMigratedType: Parser[WILLBEMIGRATED.type] = {
    "willBeMigrated" ^^ (_ => WILLBEMIGRATED)
  }

  def thisIdentifier: Parser[THISIDENTIFIER] = "this.$" ~ stringVar ^^ { str => THISIDENTIFIER(str._2) } | "this." ~ stringVar ~ ".$" ~ stringVar  ^^ { str => THISIDENTIFIER(str._1._1._2 +"_" + str._2) }

  def thisGlobalIdentifier: Parser[THISGLOBALIDENTIFIER] = "this.global.$" ~ stringVar ^^ { str => THISGLOBALIDENTIFIER(str._2) }

  def thatIdentifier: Parser[THATIDENTIFIER] = "that.$" ~ stringVar ^^ { str => THATIDENTIFIER(str._2) } | "that." ~ stringVar ~ ".$" ~ stringVar  ^^ { str => THATIDENTIFIER(str._1._1._2 +"_" + str._2) }

  def thatGlobalIdentifier: Parser[THATGLOBALIDENTIFIER] = "that.global.$" ~ stringVar ^^ { str => THATGLOBALIDENTIFIER(str._2) }

  def numeric: Parser[NUMBER] = floatingPointNumber ^^ { num => NUMBER(num.toDouble) }

  def thisEvent: Parser[THISEVENT.type] = {
    "this" ^^ (_ => THISEVENT)
  }

  def exists: Parser[EXISTS.type] = {
    "exists" ^^ (_ => EXISTS)
  }

  def global: Parser[GLOBAL.type] = {
    "global" ^^ (_ => GLOBAL)
  }

  def dot: Parser[DOT.type] = {
    "." ^^ (_ => DOT)
  }

  def comma: Parser[COMMASEPERATOR.type] = {
    "," ^^ (_ => COMMASEPERATOR)
  }

  def equals: Parser[EQUALS.type] = {
    "==" ^^ (_ => EQUALS)
  }

  def assign: Parser[ASSIGN.type] = {
    "=" ^^ (_ => ASSIGN)
  }

  def notEqual: Parser[NOTEQUAL.type] = {
    "!=" ^^ (_ => NOTEQUAL)
  }

  def greaterThan: Parser[GREATERTHAN.type] = {
    ">" ^^ (_ => GREATERTHAN)
  }

  def greaterOrEqual: Parser[GREATEROREQUAL.type] = {
    ">=" ^^ (_ => GREATEROREQUAL)
  }

  def lessThan: Parser[LESSTHAN.type] = {
    "<" ^^ (_ => LESSTHAN)
  }

  def lessOrEqual: Parser[LESSOREQUAL.type] = {
    "<=" ^^ (_ => LESSOREQUAL)
  }

  def and: Parser[AND.type] = {
    "&&" ^^ (_ => AND)
  }

  def or: Parser[OR.type] = {
    "||" ^^ (_ => OR)
  }

  def leftParenthesis: Parser[LEFTPARENTHESIS.type] = {
    "(" ^^ (_ => LEFTPARENTHESIS)
  }

  def rightParenthesis: Parser[RIGHTPARENTHESIS.type] = {
    ")" ^^ (_ => RIGHTPARENTHESIS)
  }

  def trueValue: Parser[TRUE.type] = {
    "true" ^^ (_ => TRUE)
  }

  def falseValue: Parser[FALSE.type] = {
    "false" ^^ (_ => FALSE)
  }


}
