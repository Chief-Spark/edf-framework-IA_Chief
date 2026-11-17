package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.consistencycheck.domain.DomainDictionaryInternal
import org.slf4j.LoggerFactory.getLogger

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{NoPosition, Position, Reader}

/**
 * Trigger Expression AST Parser
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step2: AST Parser for Trigger Expressions. We parse the expressions and make sure it is syntactically correct.
 * @param domainDictionary
 */
class TriggerParser(domainDictionary: DomainDictionaryInternal) extends Parsers {
  override type Elem = ConsistencyCheckToken
  @transient lazy val logger = getLogger(this.getClass)

  class TriggerTokenReader(tokens: Seq[ConsistencyCheckToken]) extends Reader[ConsistencyCheckToken] {
    override def first: ConsistencyCheckToken = tokens.head
    override def rest: Reader[ConsistencyCheckToken] = new TriggerTokenReader(tokens.tail)
    override def pos: Position = tokens.headOption.map(_.pos).getOrElse(NoPosition)
    override def atEnd: Boolean = tokens.isEmpty
  }

  def apply(tokens: Seq[ConsistencyCheckToken], checkName: String): Option[TriggerAST] = {
    val reader = new TriggerTokenReader(tokens)
    statement(reader) match {
      case NoSuccess(msg, next) => {
        logger.error("Error parsing trigger for: "+ checkName +" at line: " + next.pos.line + ", column: " + next.pos.column + " - " + msg)
        //TriggerParserError(Location(next.pos.line, next.pos.column), msg)
        None
      }
      case Success(result, next) => Some(result)
    }
  }

  def statement: Parser[TriggerAST] = {
    booleanExpressionSequence | LEFTPARENTHESIS ~>booleanExpressionSequence<~ RIGHTPARENTHESIS
  }


  def booleanExpressionSequence: Parser[BooleanExpression] = boolTerm ~ rep(AND ~ boolTerm | OR ~ boolTerm) ^^ {
    case expr1 ~ inputs => inputs.foldLeft(expr1)((a, f) => f._1 match {
      case AND => Conjunction(a, f._2)
      case OR => Disjunction(a, f._2)
      case _ => throw new UnsupportedOperationException

    })
  }

  def boolTerm: Parser[BooleanExpression] = booleanExpression | LEFTPARENTHESIS ~>booleanExpression<~ RIGHTPARENTHESIS |
    LEFTPARENTHESIS ~>booleanExpressionSequence<~ RIGHTPARENTHESIS


  def booleanExpression: Parser[BooleanExpression] = {

    val exists = EXISTS ~ LEFTPARENTHESIS ~ identifier ~ RIGHTPARENTHESIS ^^ {
      case _ ~ _ ~ THISIDENTIFIER(name) ~ _  => DoesExist(name)
    }

    val equals = identifier ~ EQUALS ~ (identifier | globalIdentifier | TRUE | FALSE | literal | numeric)  ^^ {
      case THISIDENTIFIER(name1) ~ _ ~ THISIDENTIFIER(name2) => IsThisEqualToThis(name1, name2)
      case THISIDENTIFIER(name1) ~ _ ~ THISGLOBALIDENTIFIER(name2) => IsThisEqualToGlobalThis(name1, name2)
      case THISIDENTIFIER(name1) ~ _ ~ LITERAL(name2) => IsThisEqualToLiteral(name1, name2)
      case THISIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisEqualToNumber(name1, name2,getTypeForField(name1))
      case THISIDENTIFIER(name1) ~ _ ~ TRUE => IsThisEqualToBoolean(name1, true)
      case THISIDENTIFIER(name1) ~ _ ~ FALSE => IsThisEqualToBoolean(name1, false)
    } | globalIdentifier ~ EQUALS ~ (globalIdentifier | identifier | TRUE | FALSE | literal | numeric)  ^^ {
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ THISGLOBALIDENTIFIER(name2) => IsBothGlobalThisEqualToThis(name1, name2)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ THISIDENTIFIER(name2) => IsGlobalThisEqualToThis(name1, name2)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ LITERAL(name2) => IsThisGlobalEqualToLiteral(name1, name2)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGlobalEqualToNumber(name1, name2, getTypeForField(name1))
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ TRUE => IsThisGlobalEqualToBoolean(name1, true)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ FALSE => IsThisGlobalEqualToBoolean(name1, false)
    }

    val notEqual = identifier ~ NOTEQUAL ~ (identifier | globalIdentifier | TRUE | FALSE | literal | numeric) ^^ {
      case THISIDENTIFIER(name1) ~ _ ~ THISIDENTIFIER(name2) => IsThisNotEqualToThis(name1, name2)
      case THISIDENTIFIER(name1) ~ _ ~ THISGLOBALIDENTIFIER(name2) => IsThisNotEqualToGlobalThis(name1, name2)
      case THISIDENTIFIER(name1) ~ _ ~ LITERAL(name2) => IsThisNotEqualToLiteral(name1, name2)
      case THISIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisNotEqualToNumber(name1, name2, getTypeForField(name1))
      case THISIDENTIFIER(name1) ~ _ ~ TRUE => IsThisNotEqualToBoolean(name1, true)
      case THISIDENTIFIER(name1) ~ _ ~ FALSE => IsThisNotEqualToBoolean(name1, false)
    } | globalIdentifier ~ EQUALS ~ (globalIdentifier | identifier | TRUE | FALSE | literal | numeric)  ^^ {
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ THISGLOBALIDENTIFIER(name2) => IsBothGlobalThisNotEqualToThis(name1, name2)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ THISIDENTIFIER(name2) => IsGlobalThisNotEqualToThis(name1, name2)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ LITERAL(name2) => IsThisGlobalNotEqualToLiteral(name1, name2)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGlobalNotEqualToNumber(name1, name2, getTypeForField(name1))
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ TRUE => IsThisGlobalNotEqualToBoolean(name1, true)
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ FALSE => IsThisGlobalNotEqualToBoolean(name1, false)
    }

    val less = identifier ~ LESSTHAN ~ ( numeric) ^^ {
      case THISIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisLess(name1, name2, getTypeForField(name1))
    } | globalIdentifier ~ LESSTHAN ~ ( numeric) ^^ {
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGlobalLess(name1, name2, getTypeForField(name1))
    }

    val lessOrEqual = identifier ~ LESSOREQUAL  ~ ( numeric) ^^ {
      case THISIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisLessOrEqual(name1, name2, getTypeForField(name1))
    } | globalIdentifier ~ LESSOREQUAL  ~ ( numeric) ^^ {
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGlobalLessOrEqual(name1, name2, getTypeForField(name1))
    }

    val greater = identifier ~ GREATERTHAN ~ ( numeric) ^^ {
      case THISIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGreater(name1, name2, getTypeForField(name1))
    } | globalIdentifier ~ GREATERTHAN ~ ( numeric) ^^ {
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGlobalGreater(name1, name2, getTypeForField(name1))
    }

    val greatOrEqual = identifier ~ GREATEROREQUAL ~ (identifier | TRUE | FALSE | literal | numeric) ^^ {
      case THISIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGreaterOrEqual(name1, name2, getTypeForField(name1))
    } | globalIdentifier ~ GREATEROREQUAL ~ (identifier | TRUE | FALSE | literal | numeric) ^^ {
      case THISGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsThisGlobalGreaterOrEqual(name1, name2, getTypeForField(name1))
    }

    val anyIdentifier = ANYINDENTIFIER ^^ {
      case _ => anyEvent()
    }

    anyIdentifier | exists | equals | notEqual | lessOrEqual | less | greatOrEqual | greater
  }
  // TODO Add Variables names to a set
  // TODO Change Lit to take any type - integer nad string
  private def identifier: Parser[THISIDENTIFIER] = positioned {
    accept("identifier", { case id @ THISIDENTIFIER(name) => id })
  }

  private def globalIdentifier: Parser[THISGLOBALIDENTIFIER] = positioned {
    accept("identifier", { case id @ THISGLOBALIDENTIFIER(name) => id })
  }


  private def literal: Parser[LITERAL] = positioned {
    accept("string literal", { case lit @ LITERAL(name) => lit })
  }

  private def numeric: Parser[NUMBER] = positioned {
    accept("numeric", { case lit @ NUMBER(name) => lit })
  }

  private def getTypeForField(fieldName: String): String = {
    ASTUtil.getFieldTypeFromDomainDictionary(fieldName, domainDictionary)
  }

}
