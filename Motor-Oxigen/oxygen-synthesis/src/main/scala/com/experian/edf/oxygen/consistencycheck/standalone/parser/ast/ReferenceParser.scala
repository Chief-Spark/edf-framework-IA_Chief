package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.consistencycheck.domain.DomainDictionaryInternal
import org.slf4j.LoggerFactory.getLogger

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{NoPosition, Position, Reader}

/**
 * Reference Expression Parser
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step2: AST Parser for Reference Expressions. We parse the expressions and make sure it is syntactically correct.
 * @param domainDictionary
 */
class ReferenceParser(domainDictionary: DomainDictionaryInternal) extends Parsers {
  override type Elem = ConsistencyCheckToken
  @transient lazy val logger = getLogger(this.getClass)

  class ReferenceTokenReader(tokens: Seq[ConsistencyCheckToken]) extends Reader[ConsistencyCheckToken] {
    override def first: ConsistencyCheckToken = tokens.head
    override def rest: Reader[ConsistencyCheckToken] = new ReferenceTokenReader(tokens.tail)
    override def pos: Position = tokens.headOption.map(_.pos).getOrElse(NoPosition)
    override def atEnd: Boolean = tokens.isEmpty
  }

  def apply(tokens: Seq[ConsistencyCheckToken], checkName: String): Option[ReferenceAST] = {
    val reader = new ReferenceTokenReader(tokens)
    statement(reader) match {
      case NoSuccess(msg, next) => {
        logger.error("Error parsing reference for check: "+ checkName +" at line: " + next.pos.line + ", column: " + next.pos.column + " - " + msg)
        None
      }
      case Success(result, next) => Some(result)
    }
  }

  def statement: Parser[ReferenceAST] = {
     booleanExpressionSequence | LEFTPARENTHESIS ~>booleanExpressionSequence<~ RIGHTPARENTHESIS
  }

  def booleanExpressionSequence: Parser[RefBooleanExpression] = boolTerm ~ rep(AND ~ boolTerm | OR ~ boolTerm) ^^ {
    case expr1 ~ inputs => inputs.foldLeft(expr1)((a, f) => f._1 match {
      case AND => RefConjunction(a, f._2)
      case OR => RefDisjunction(a, f._2)
      case _ => throw new UnsupportedOperationException

    })
  }

  def boolTerm: Parser[RefBooleanExpression] = booleanExpression | LEFTPARENTHESIS ~>booleanExpression<~ RIGHTPARENTHESIS |
    LEFTPARENTHESIS ~>booleanExpressionSequence<~ RIGHTPARENTHESIS

  def booleanExpression: Parser[RefBooleanExpression] = {
    //** EXISTS
    val thisExists = EXISTS ~ LEFTPARENTHESIS ~ thisIdentifier ~ RIGHTPARENTHESIS ^^ {
      case _ ~ _ ~ THISIDENTIFIER(name) ~ _  => DoesExistInThis(name)
    }
    val thatExists = EXISTS ~ LEFTPARENTHESIS ~ thatIdentifier ~ RIGHTPARENTHESIS ^^ {
      case _ ~ _ ~ THATIDENTIFIER(name) ~ _  => DoesExistInThat(name)
    }

    //** EQUALS
    val equals = thisIdentifier ~ EQUALS ~ (thatIdentifier | thatGlobalIdentifier)  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsThisEqualToThat(thisName, thatName)
      case THISIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsThisEqualToGlobalThat(thisName, thatName)

    } | thatIdentifier ~ EQUALS ~ (thisIdentifier | thisGlobalIdentifier | TRUE | FALSE | literal | numeric)  ^^ {
        case THATIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThisEqualToThat(thisName, thatName)
        case THATIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsGlobalThisEqualToThat(thisName, thatName)
        case THATIDENTIFIER(thatName) ~ _ ~ TRUE => IsThatEqualToBoolean(thatName, true)
        case THATIDENTIFIER(thatName) ~ _ ~ FALSE => IsThatEqualToBoolean(thatName, false)
        case THATIDENTIFIER(thatName) ~ _ ~ LITERAL(name2) => IsThatEqualToLiteral(thatName, name2)
        case THATIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsThatEqualToNumber(thatName, name2, getTypeForField(thatName))

    } | thisGlobalIdentifier ~ EQUALS ~ (thatGlobalIdentifier | thatIdentifier)  ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsBothGlobalThisEqualToThat(thisName, thatName)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsGlobalThisEqualToThat(thisName, thatName)


    } | thatGlobalIdentifier ~ EQUALS ~ (thisGlobalIdentifier | thisIdentifier | TRUE | FALSE | literal | numeric)  ^^ {
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsBothGlobalThisEqualToThat(thisName, thatName)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsGlobalThisEqualToThat(thisName, thatName)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ TRUE => IsGlobalThatEqualToBoolean(thatName, true)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ FALSE => IsGlobalThatEqualToBoolean(thatName, false)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ LITERAL(name2) => IsGlobalThatEqualToLiteral(thatName, name2)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsGlobalThatEqualToNumber(thatName, name2, getTypeForField(thatName))
    }

    //** NOTEQUALS
    val notEquals = thisIdentifier ~ NOTEQUAL ~ (thatIdentifier | thatGlobalIdentifier) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsThisNotEqualToThat(thisName, thatName)
      case THISIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsThisNotEqualToGlobalThat(thisName, thatName)

    } | thatIdentifier ~ NOTEQUAL ~ (thisIdentifier | thisGlobalIdentifier | TRUE | FALSE | literal | numeric)  ^^ {
      case THATIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThisNotEqualToThat(thisName, thatName)
      case THATIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsThatNotEqualToGlobalThis( thisName, thatName)
      case THATIDENTIFIER(thatName) ~ _ ~ TRUE => IsThatNotEqualToBoolean(thatName, true)
      case THATIDENTIFIER(thatName) ~ _ ~ FALSE => IsThatNotEqualToBoolean(thatName, false)
      case THATIDENTIFIER(thatName) ~ _ ~ LITERAL(name2) => IsThatNotEqualToLiteral(thatName, name2)
      case THATIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsThatNotEqualToNumber(thatName, name2, getTypeForField(thatName))

    } | thisGlobalIdentifier ~ NOTEQUAL ~ (thatGlobalIdentifier | thatIdentifier) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsBothGlobalThisNotEqualToThat(thisName, thatName)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsGlobalThisNotEqualToThat(thisName, thatName)

    } | thatGlobalIdentifier ~ NOTEQUAL ~ (thisGlobalIdentifier | thisIdentifier | TRUE | FALSE | literal | numeric)  ^^ {
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsBothGlobalThisNotEqualToThat(thisName, thatName)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThatNotEqualToGlobalThis(thisName, thatName)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ TRUE => IsGlobalThatNotEqualToBoolean(thatName, true)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ FALSE => IsGlobalThatNotEqualToBoolean(thatName, false)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ LITERAL(name2) => IsGlobalThatNotEqualToLiteral(thatName, name2)
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsGlobalThatNotEqualToNumber(thatName, name2, getTypeForField(thatName))

    }

    //** GREATER
    val greater = thisIdentifier ~ GREATERTHAN ~ (thatIdentifier | thatGlobalIdentifier )  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsThisGreaterThanThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsThisGreaterThanGlobalThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatIdentifier ~ GREATERTHAN ~ (thisIdentifier | thisGlobalIdentifier | numeric)  ^^ {
      case THATIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThatGreaterThanThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsThatGreaterThanGlobalThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsThatGreaterThanNumber(thatName, name2, getTypeForField(thatName))

    } | thisGlobalIdentifier ~ GREATERTHAN ~ (thatGlobalIdentifier | thatIdentifier )  ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsBothGlobalThisGreaterThanThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsGlobalThisGreaterThanThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatGlobalIdentifier ~ GREATERTHAN ~ (thisGlobalIdentifier | thisIdentifier | numeric)  ^^ {
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsBothGlobalThatGreaterThanThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsGlobalThatGreaterThanThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsBothGlobalThatGreaterThanNumber(thatName, name2, getTypeForField(thatName))
    }

    //** GREATER OR EQUALS
    val greaterOrEqual = thisIdentifier ~ GREATEROREQUAL ~ (thatIdentifier | thatGlobalIdentifier )  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsThisGreaterThanEqualsThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsThisGreaterThanEqualsGlobalThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatIdentifier ~ GREATEROREQUAL ~ (thisIdentifier | thisGlobalIdentifier | numeric)  ^^ {
      case THATIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThatGreaterThanEqualsThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsThatGreaterThanEqualsGlobalThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsThatGreaterThanEqualsNumber(thatName, name2, getTypeForField(thatName))

    } | thisGlobalIdentifier ~ GREATEROREQUAL ~ (thatGlobalIdentifier | thatIdentifier )  ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsBothGlobalThisGreaterThanEqualsThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsGlobalThisGreaterThanEqualsThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatGlobalIdentifier ~ GREATEROREQUAL ~ (thisGlobalIdentifier |thisIdentifier | numeric)  ^^ {
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsBothGlobalThatGreaterThanEqualsThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsGlobalThatGreaterThanEqualsThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(name1) ~ _ ~ NUMBER(name2) => IsBothGlobalThatGreaterThanEqualsNumber(name1, name2, getTypeForField(name1))
    }

    //** LESS
    val less = thisIdentifier ~ LESSTHAN ~ (thatIdentifier | thatGlobalIdentifier )  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsThisLessThanThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsThisLessThanGlobalThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatIdentifier ~ LESSTHAN ~ (thisIdentifier | thisGlobalIdentifier | numeric)  ^^ {
      case THATIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThatLessThanThis(thatName, thisName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsThatLessThanGlobalThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsThatLessThanNumber(thatName, name2, getTypeForField(thatName))

    } | thisGlobalIdentifier ~ LESSTHAN ~ (thatGlobalIdentifier | thatIdentifier )  ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsBothGlobalThisLessThanThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsGlobalThisLessThanThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatGlobalIdentifier ~ LESSTHAN ~ (thisGlobalIdentifier | thisIdentifier | numeric)  ^^ {
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsBothGlobalThatLessThanThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsGlobalThatLessThanThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsGlobalThatLessThanNumber(thatName, name2, getTypeForField(thatName))
    }

    //** LESS OR EQUALS
    val lessOrEqual = thisIdentifier ~ LESSOREQUAL ~ (thatIdentifier | thatGlobalIdentifier )  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsThisLessThanEqualsThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsThisLessThanEqualsGlobalThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatIdentifier ~ LESSOREQUAL ~ (thisIdentifier | numeric | thisGlobalIdentifier)  ^^ {
      case THATIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsThatLessThanEqualsThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(name2) => IsThatLessThanEqualsGlobalThis(thatName, name2, getAndCompareThisThatFieldTypes(thatName, name2))
      case THATIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsThatLessThanEqualsNumber(thatName, name2, getTypeForField(thatName))

    } | thisGlobalIdentifier ~ LESSOREQUAL ~ (thatGlobalIdentifier | thatIdentifier )  ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATGLOBALIDENTIFIER(thatName) => IsBothGlobalThisLessThanEqualsThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ THATIDENTIFIER(thatName) => IsGlobalThisLessThanEqualsThat(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | thatGlobalIdentifier ~ LESSOREQUAL ~ (thisGlobalIdentifier | thisIdentifier | numeric)  ^^ {
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISGLOBALIDENTIFIER(thisName) => IsBothGlobalThatLessThanEqualsThis(thatName, thisName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ THISIDENTIFIER(thisName) => IsGlobalThatLessThanEqualsThis(thisName, thatName, getAndCompareThisThatFieldTypes(thatName, thisName))
      case THATGLOBALIDENTIFIER(thatName) ~ _ ~ NUMBER(name2) => IsGlobalThatLessThanEqualsNumber(thatName, name2, getTypeForField(thatName))
    }

    thisExists | thatExists | equals | notEquals | lessOrEqual | less | greaterOrEqual | greater
  }

  private def thisIdentifier: Parser[THISIDENTIFIER] = positioned {
    accept("thisIdentifier", { case id @ THISIDENTIFIER(name) => id })
  }

  private def thisGlobalIdentifier: Parser[THISGLOBALIDENTIFIER] = positioned {
    accept("thisIdentifier", { case id @ THISGLOBALIDENTIFIER(name) => id })
  }

  private def thatIdentifier: Parser[THATIDENTIFIER] = positioned {
    accept("identifier", { case id @ THATIDENTIFIER(name) => id })
  }

  private def thatGlobalIdentifier: Parser[THATGLOBALIDENTIFIER] = positioned {
    accept("identifier", { case id @ THATGLOBALIDENTIFIER(name) => id })
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

  private def getAndCompareThisThatFieldTypes(name1: String, name2: String): String = {
    val t1 = ASTUtil.getFieldTypeFromDomainDictionary(name1, domainDictionary)
    val t2 = ASTUtil.getFieldTypeFromDomainDictionary(name2, domainDictionary)
    if (t1 == t2)
      t1
    else
      { logger.error("This and That type do not match in domain dictionary for fields: " + name1 + ":" +t1+ " " + name2 + ":"+ t2 + " ,using String type")
        DomainDictionaryInternal.StringType
      }
    // TODO: Error or Exception and break?
  }
}
