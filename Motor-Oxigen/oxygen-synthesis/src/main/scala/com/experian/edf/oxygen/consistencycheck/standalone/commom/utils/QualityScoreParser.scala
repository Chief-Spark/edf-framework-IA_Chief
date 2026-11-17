package com.experian.edf.oxygen.consistencycheck.standalone.commom.utils

import com.experian.edf.oxygen.validator._

import scala.util.parsing.combinator.JavaTokenParsers


/**
  * This parser will be used to evaluate quality scores and come up
  * with a decision to either accept the file or reject it.
  * The rules may be of the form
  * qs1 * wtqs1 + qs2 / wtqs2 - (qs3 * wtqs3 + qs4 * wtqs4) > 10 and so on.
  * This quality parser will be able to evaluate expressions. The BNF for this
  * parser could be something as follows:
  *
  * boolExpr ::= boolTerm {"or" | "and"}.
  * or ::= "or" {boolTerm}.
  * and ::= "and" {boolTerm}.
  * boolTerm ::= boolLiteral | boolArithExpr | "(" ~> boolExpr <~ ")".
  * boolLiteral ::= "true" | "false".
  * boolArithGtExpr ::= arithExpr gt.
  * boolArithGeExpr ::= arithExpr ge.
  * boolArithLtExpr ::= arithExpr lt.
  * boolArithLeExpr ::= anyExpr le.
  * boolArithEqExpr ::= anyExpr eq.
  * boolArithExpr ::= boolArithGtExpr | boolArithGeExpr | boolArithLtExpr | boolArithLeExpr | boolArithEqExpr | boolArithNeExpr.
  * boolStrEqExpr ::= stringLiteral eq.
  * boolStrNeExpr ::= stringLiteral ne.
  * boolStrExpr ::= boolStrEqExpr | boolStrneExpr
  * gt ::= ">" {arithExpr}.
  * ge ::= ">=" {arithExpr}.
  * lt ::= "<" {arithExpr}.
  * le ::= "<=" {arithExpr}.
  * eq ::= "==" {arithExpr}.
  * mod ::= "%" {arithExpr}.
  * arithExpr ::= arithTerm { plus | minus}.
  * anyExpr ::= arithExpr | stringLiteral
  * plus ::= "+" arithTerm.
  * minus ::= "-" arithTerm.
  * arithTerm ::= arithFactor {times | divide}.
  * times ::= "*" arithFactor.
  * divide ::= "/" arithFactor.
  * arithFactor ::= fpn | "(" ~> arithExpr <~ ")".
  * fpn ::= floatingPointNumber.
  * stddev ::= "(" ~> arithExpr <~ ")".
  */
class QualityScoreParser extends JavaTokenParsers {

  case class ErrorCodeCount(errors : Array[ObjectError])  {

    override def toString: String = if (!errors.isEmpty) errors.length.toString else 0.toString
  }

  type D = Double
  type B = Boolean
  type I = Int
  type A = Any

  // Boolean expressions
  def boolExpr : Parser[B] = boolTerm~rep(or|and) ^^ {case a~b => (a /: b)((acc,f) => f(acc))}
  def or: Parser[B => B] = "or"~boolTerm ^^ {case "or"~b => _ || b}
  def and: Parser[B => B] = "and"~boolTerm ^^ {case "and"~b => _ && b}
  def boolTerm: Parser[B] = boolLiteral | boolStrExpr | boolArithExpr | "(" ~> boolExpr <~ ")"
  def boolLiteral: Parser[B] = "true" ^^ (x => true) | "false" ^^ (x => false)

  // TODO: Need to combine ge|gt|lt|le|eq into one expression.
  // def boolArithExpr: Parser[Any] = arithExpr~rep(gt|ge|lt|le|eq) ^^ {case a~b => (a /: b) ((acc,f) => f(acc))}

  // TODO: Can definitely have a better way of rewriting these expressions for arithmetic and string expressions
  def boolArithGtExpr: Parser[B] = arithExpr~gt ^^ {case a~b => b(a)}
  def boolArithGeExpr: Parser[B] = arithExpr~ge ^^ {case a~b => b(a)}
  def boolArithLtExpr: Parser[B] = arithExpr~lt ^^ {case a~b => b(a)}
  def boolArithLeExpr: Parser[B] = arithExpr~le ^^ {case a~b => b(a)}
  def boolArithEqExpr: Parser[B] = arithExpr~eq ^^ {case a~b => b(a)}
  def boolArithNeExpr: Parser[B] = arithExpr~ne ^^ {case a~b => b(a)}
  def boolStrEqExpr: Parser[B] = stringLiteral~eq ^^ {case a~b => b(a)}
  def boolStrNeExpr: Parser[B] = stringLiteral~ne ^^ {case a~b => b(a)}
  def boolStrExpr : Parser[B] = boolStrEqExpr | boolStrNeExpr
  // def boolArithExpr: Parser[B] = arithExpr~rep(gt|ge|lt|le|eq|ne) ^^  {case a~b => (b foldLeft a)()}
  def boolArithExpr: Parser[B] = boolArithGtExpr | boolArithGeExpr | boolArithLtExpr | boolArithLeExpr | boolArithEqExpr | boolArithNeExpr

  def gt: Parser[D => B] = ">" ~ arithExpr ^^ {case ">"~b => _ > b}
  def ge: Parser[D => B] = ">=" ~ arithExpr ^^ {case ">="~b => _ >= b}
  def lt: Parser[D => B] = "<" ~ arithExpr ^^ {case "<"~b => _ < b}
  def le: Parser[D => B] = "<=" ~ arithExpr ^^ {case "<="~b => _ <= b}
  def eq: Parser[A => B] = "==" ~ anyExpr ^^ {case "=="~b => _ == b}
  def ne: Parser[A => B] = "!=" ~ anyExpr ^^ {case "!="~b => _ != b}

  def anyExpr : Parser[A] = arithExpr | stringLiteral

  def arithExpr: Parser[D] = arithTerm~rep(plus|minus) ^^ {case a~b => (a /: b)((acc,f) => f(acc))}
  def plus: Parser[D => D] = "+"~arithTerm ^^ {case "+"~b => _ + b}
  def minus: Parser[D => D] = "-"~arithTerm ^^ {case "-"~b => _ - b}
  def arithTerm: Parser[D] = arithFactor~rep(times | divide) ^^ {case a~b => (a /: b)((acc,f) => f(acc))}
  def times: Parser[D => D] = "*"~arithFactor ^^ {case "*"~b => _ * b}
  def divide: Parser[D => D] = "/"~arithFactor ^^ {case "/"~b => _ / b}
  def arithFactor: Parser[D] = fpn | "(" ~> arithExpr <~ ")"
  def fpn: Parser[D] = floatingPointNumber ^^ (_.toDouble)

  def equals: Parser[A => B] = "eq" ~ stringLiteral ^^ {case "eq"~b => _ == b}
}





