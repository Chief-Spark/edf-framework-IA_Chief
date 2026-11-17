package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import org.slf4j.LoggerFactory.getLogger

import scala.util.parsing.combinator.RegexParsers

/**
 * Reference Expression Lexical Analysis
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
object ReferenceLexer extends RegexParsers {
  override def skipWhitespace = true
  override val whiteSpace = "[ \t\r\f]+".r
  private val stringVar: Parser[String] = "[a-z,A-Z,0-9,_]+".r
  private val floatingPointNumber: Parser[String] =
    """-?(\d+(\.\d*)?|\d*\.\d+)([eE][+-]?\d+)?[fFdD]?""".r
  @transient lazy val logger = getLogger(this.getClass)


  def apply(code: String, checkName: String): Option[List[ConsistencyCheckToken]] = {
    parse(tokens, code) match {
      case NoSuccess(msg, next) => {
        logger.error("Error lexer reference for check: "+ checkName +" at line: " + next.pos.line + ", column: " + next.pos.column + " - " + msg)
        None
      }
      case Success(result, next) => Some(result)
    }
  }

  def tokens: Parser[List[ConsistencyCheckToken]] = {
    phrase(rep1(   thisGlobalIdentifier | thisIdentifier | thatGlobalIdentifier | thatIdentifier | dot | exists | global  | equals | notEqual | greaterOrEqual | greaterThan
      | lessOrEqual | lessThan | trueValue | falseValue  | numeric | literal | identifier  | and | or |
      leftParenthesis | rightParenthesis )) ^^ { rawTokens =>
      rawTokens
    }
  }

  def identifier: Parser[IDENTIFIER] =
    "[a-zA-Z_0-9]+".r ^^ { str => IDENTIFIER(str) }

  def literal: Parser[LITERAL] =  {
    """"[^"]*"""".r ^^ { str =>
      val content = str.substring(1, str.length - 1)
      LITERAL(content)
    }
  }

  def thisIdentifier: Parser[THISIDENTIFIER] = "this.$" ~ stringVar ^^ { str => THISIDENTIFIER(str._2) } | "this." ~ stringVar ~ ".$" ~ stringVar  ^^ { str => THISIDENTIFIER(str._1._1._2 +"_" + str._2) }
  def thisGlobalIdentifier: Parser[THISGLOBALIDENTIFIER] = "this.global.$" ~ stringVar ^^ { str => THISGLOBALIDENTIFIER(str._2) }
  def thatIdentifier: Parser[THATIDENTIFIER] = "that.$" ~ stringVar ^^ { str => THATIDENTIFIER(str._2) } | "that." ~ stringVar ~ ".$" ~ stringVar  ^^ { str => THATIDENTIFIER(str._1._1._2 +"_" + str._2) }
  def thatGlobalIdentifier: Parser[THATGLOBALIDENTIFIER] = "that.global.$" ~ stringVar ^^ { str => THATGLOBALIDENTIFIER(str._2) }
  def numeric: Parser[NUMBER] = floatingPointNumber ^^ { num => NUMBER(num.toDouble)}
  def thisEvent: Parser[THISEVENT.type] =  { "this" ^^ ( _ => THISEVENT ) }
  def exists: Parser[EXISTS.type] =  { "exists" ^^ ( _ => EXISTS ) }
  def global: Parser[GLOBAL.type] = { "global" ^^ (_ => GLOBAL)}
  def dot: Parser[DOT.type] = { "." ^^ (_ => DOT)}
  def equals: Parser[EQUALS.type] = { "==" ^^ (_ => EQUALS)}
  def notEqual: Parser[NOTEQUAL.type] = { "!=" ^^ (_ => NOTEQUAL)}
  def greaterThan: Parser[GREATERTHAN.type] = { ">" ^^ (_ => GREATERTHAN)}
  def greaterOrEqual: Parser[GREATEROREQUAL.type] = { ">=" ^^ (_ => GREATEROREQUAL)}
  def lessThan: Parser[LESSTHAN.type] = { "<" ^^ (_ => LESSTHAN)}
  def lessOrEqual: Parser[LESSOREQUAL.type] = { "<=" ^^ (_ => LESSOREQUAL)}
  def and: Parser[AND.type] = { "&&" ^^ (_ => AND)}
  def or: Parser[OR.type] = {"||" ^^ (_ => OR)}
  def leftParenthesis: Parser[LEFTPARENTHESIS.type] = {"(" ^^ (_ => LEFTPARENTHESIS)}
  def rightParenthesis: Parser[RIGHTPARENTHESIS.type] = {")" ^^ (_ => RIGHTPARENTHESIS)}
  def trueValue: Parser[TRUE.type] = {"true" ^^ (_ => TRUE)}
  def falseValue: Parser[FALSE.type] = {"false" ^^ (_ => FALSE)}


}
