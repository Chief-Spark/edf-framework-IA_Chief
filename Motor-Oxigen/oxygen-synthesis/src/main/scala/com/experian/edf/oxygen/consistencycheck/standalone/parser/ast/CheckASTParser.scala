package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.consistencycheck.domain.DomainDictionaryInternal
import org.slf4j.LoggerFactory.getLogger

import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{NoPosition, Position, Reader}

/**
 * Check Expression AST Parser
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step2: AST Parser for Check Expressions. We parse the expressions and make sure it is syntactically correct.
 * @param domainDictionary
 */
class CheckASTParser(domainDictionary: DomainDictionaryInternal) extends Parsers with Serializable {
  override type Elem = ConsistencyCheckToken
  @transient lazy val logger = getLogger(this.getClass)

  class CheckTokenReader(tokens: Seq[ConsistencyCheckToken]) extends Reader[ConsistencyCheckToken] {
    override def first: ConsistencyCheckToken = tokens.head
    override def rest: Reader[ConsistencyCheckToken] = new CheckTokenReader(tokens.tail)
    override def pos: Position = tokens.headOption.map(_.pos).getOrElse(NoPosition)
    override def atEnd: Boolean = tokens.isEmpty
  }

  def apply(tokens: Seq[ConsistencyCheckToken], checkName: String): Option[CheckAST] = {
    val reader = new CheckTokenReader(tokens)
    statement(reader) match {
      case NoSuccess(msg, next) => {
        logger.error("Error parsing check condition for check: "+ checkName +" at line: " + next.pos.line + ", column: " + next.pos.column + " - " + msg)
        None
      }
      case Success(result, next) => Some(result)
    }
  }

  def statement: Parser[CheckAST] = {
     booleanExpressionSequence | LEFTPARENTHESIS ~>booleanExpressionSequence<~ RIGHTPARENTHESIS
  }


  def booleanExpressionSequence: Parser[CheckBooleanExpression] = boolTerm ~ rep(AND ~ boolTerm | OR ~ boolTerm) ^^ {
    case expr1 ~ inputs => inputs.foldLeft(expr1)((a, f) => f._1 match {
      case AND => CheckConjunction(a, f._2)
      case OR => CheckDisjunction(a, f._2)
      case _ => throw new UnsupportedOperationException
    })
  }

  def boolTerm: Parser[CheckBooleanExpression] = booleanExpression | LEFTPARENTHESIS ~>booleanExpression<~ RIGHTPARENTHESIS |
    LEFTPARENTHESIS ~>booleanExpressionSequence<~ RIGHTPARENTHESIS


  /**
   * THAT EXPRESSION FUNCTIONS
   * @return
   */
  def mostRecentFunc: Parser[(CheckFunctionExpression, String)] = MOSTRECENT ~ LEFTPARENTHESIS ~ (thatGlobalIdentifier | thatIdentifier) ~ RIGHTPARENTHESIS ^^ {
    case _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => (MostRecentValue(thatName), thatName)
    case _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => (MostRecentGlobalValue(thatName), thatName)
  }

  def checkStateFunc: Parser[(CheckFunctionExpressionState, String)] = CHECKSTATE ~ LEFTPARENTHESIS ~ identifier ~ ASSIGN ~ identifier ~ COMMASEPERATOR ~ identifier ~ ASSIGN ~ identifier ~ COMMASEPERATOR ~ thisGlobalIdentifier ~ RIGHTPARENTHESIS ^^ {
    case x ~ stateId ~ _ ~ fieldId ~ _ => (
      CheckStateValue(x._1._1._1._2.str, stateId.str, fieldId.str), stateId.str)
  }

  def lastFunc: Parser[(CheckFunctionExpression, String)] = LAST ~ LEFTPARENTHESIS ~ (thatGlobalIdentifier | thatIdentifier) ~ RIGHTPARENTHESIS  ^^ {
    case _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => (LastValue(thatName), thatName)
    case _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => (LastGlobalValue(thatName), thatName)
  }

  def firstFunc: Parser[(CheckFunctionExpression, String)] = FIRST ~ LEFTPARENTHESIS ~ (thatGlobalIdentifier | thatIdentifier) ~ RIGHTPARENTHESIS  ^^ {
    case _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => (FirstValue(thatName), thatName)
    case _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => (FirstGlobalValue(thatName), thatName)
  }

  def anyOneFunc: Parser[(CheckFunctionExpression, String)] = ANYONE ~ LEFTPARENTHESIS ~ (thatGlobalIdentifier | thatIdentifier) ~ RIGHTPARENTHESIS  ^^ {
    case _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => (AnyOneValue(thatName), thatName)
    case _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => (AnyOneGlobalValue(thatName), thatName)
  }

  // TODO: prevPeriod
  def prevPeriodFunc: Parser[(CheckFunctionExpression, String)] = PREV ~ LEFTPARENTHESIS ~ numeric ~ COMMASEPERATOR ~(thatGlobalIdentifier | thatIdentifier) ~ RIGHTPARENTHESIS  ^^ {
    case _ ~ _ ~ _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => (AnyOneValue(thatName), thatName)
    case _ ~ _ ~ _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => (AnyOneGlobalValue(thatName), thatName)
  }

  def referenceCount: Parser[(CheckFunctionExpression, String)] = REFERENCEEVENTCOUNT ^^ {
    _ => (ReferenceEventCountValue(), DomainDictionaryInternal.IntType)
  }

//  def thatExpressionFunctions: Parser[(CheckFunctionExpression, String)] = mostRecentFunc | firstFunc | lastFunc | anyOneFunc | prevPeriodFunc | checkStateFunc
  def thatExpressionFunctions: Parser[(CheckFunctionExpression, String)] = mostRecentFunc | firstFunc | lastFunc | anyOneFunc | prevPeriodFunc
  def thatExpressionFunctionsState: Parser[(CheckFunctionExpressionState, String)] = checkStateFunc

  def filterExpression: Parser[Map[String, CheckFunctionExpression]] = identifier ~ EQUALS ~ thisIdentifier ^^ {
    case IDENTIFIER(name) ~ _ ~ THISIDENTIFIER(thisName) =>  Map(name -> GetThisTermValue(thisName))
  } | identifier ~ EQUALS ~ thatExpressionFunctions ^^ {
    case IDENTIFIER(name) ~ _ ~ value => Map(name -> value._1)
  }

  def fieldMap: Parser[Map[String, CheckFunctionExpression]] = repsep(filterExpression, COMMASEPERATOR) ^^ { x => x.reduce(_++_)}

  /**
   * booleanExpression
   * @return
   */
  def booleanExpression: Parser[CheckBooleanExpression] = {

    //** equals
    val equals =  (thisIdentifier | thisGlobalIdentifier) ~ EQUALS ~ thatExpressionFunctions  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ value =>
        IsThisEqualsAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ value =>
        IsGlobalThisEqualsAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(thisName, value._2))

    } | thatExpressionFunctions ~ EQUALS ~ (TRUE | FALSE | literal | numeric) ^^ {
      case thatFuncValue ~ _ ~ TRUE => IsEqualsAnyFunctionToBoolean(thatFuncValue._1, true)
      case thatFuncValue ~ _ ~ FALSE => IsEqualsAnyFunctionToBoolean(thatFuncValue._1, false)
      case thatFuncValue ~ _ ~ LITERAL(str) => IsEqualsAnyFunctionToLiteral(thatFuncValue._1, str)
      case thatFuncValue ~ _ ~ NUMBER(value) => IsEqualsAnyFunctionWithType(thatFuncValue._1, value, getTypeForField(thatFuncValue._2))

    } | thisIdentifier ~ EQUALS ~ (TRUE | FALSE | literal | numeric) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ TRUE => IsThisEqualToBooleanForCheck(thisName, true)
      case THISIDENTIFIER(thisName) ~ _ ~ FALSE => IsThisEqualToBooleanForCheck(thisName, false)
      case THISIDENTIFIER(thisName) ~ _ ~ LITERAL(str) => IsThisEqualToLiteralForCheck(thisName, str)
      case THISIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisEqualToNumberForCheck(thisName, value, getTypeForField(thisName))

    } | thisGlobalIdentifier ~ EQUALS ~ (TRUE | FALSE | literal | numeric) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ TRUE => IsThisGlobalEqualToBooleanForCheck(thisName, true)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ FALSE => IsThisGlobalEqualToBooleanForCheck(thisName, false)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ LITERAL(str) => IsThisGlobalEqualToLiteralForCheck(thisName, str)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGlobalEqualToNumberForCheck(thisName, value, getTypeForField(thisName))

    }| thatExpressionFunctionsState ~ EQUALS ~ (TRUE | FALSE | literal | numeric) ^^ {
      case thatFuncValue ~ _ ~ TRUE => IsEqualsAnyFunctionToBooleanStateCheck(thatFuncValue._1, true)
      case thatFuncValue ~ _ ~ FALSE => IsEqualsAnyFunctionToBooleanStateCheck(thatFuncValue._1, false)

    } | referenceCount ~ EQUALS ~ numeric ^^ {
      case referenceCount ~ _ ~ NUMBER(value) => IsEqualsAnyFunctionWithType(referenceCount._1, value, referenceCount._2)

    } | WILLBEMIGRATED ~ EQUALS ~ (TRUE | FALSE) ^^ {
      case _ ~ _ ~ TRUE => WillBeMigratedEqualsBoolean(true)
      case _ ~ _ ~ FALSE => WillBeMigratedEqualsBoolean(false)
    }

    //** not Equals
    val notEquals =  (thisIdentifier | thisGlobalIdentifier) ~ NOTEQUAL ~ thatExpressionFunctions  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ value =>
        IsThisNotEqualsAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ value =>
        IsGlobalThisNotEqualsAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))

    } | thatExpressionFunctions ~ NOTEQUAL ~ (TRUE | FALSE | literal | numeric) ^^ {
      case thatFuncValue ~ _ ~ TRUE => IsNotEqualsAnyFunctionToBoolean(thatFuncValue._1, true)
      case thatFuncValue ~ _ ~ FALSE => IsNotEqualsAnyFunctionToBoolean(thatFuncValue._1, false)
      case thatFuncValue ~ _ ~ LITERAL(str) => IsNotEqualsAnyFunctionToLiteral(thatFuncValue._1, str)
      case thatFuncValue ~ _ ~ NUMBER(value) => IsNotEqualsAnyFunctionWithType(thatFuncValue._1, value, getTypeForField(thatFuncValue._2))

    } | thisIdentifier ~ NOTEQUAL ~ (TRUE | FALSE | literal | numeric) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ TRUE => IsThisNotEqualToBooleanForCheck(thisName, true)
      case THISIDENTIFIER(thisName) ~ _ ~ FALSE => IsThisNotEqualToBooleanForCheck(thisName, false)
      case THISIDENTIFIER(thisName) ~ _ ~ LITERAL(str) => IsThisNotEqualToLiteralForCheck(thisName, str)
      case THISIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisNotEqualToNumberForCheck(thisName, value, getTypeForField(thisName))

    } | thisGlobalIdentifier ~ NOTEQUAL ~ (TRUE | FALSE | literal | numeric) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ TRUE => IsThisGlobalNotEqualToBooleanForCheck(thisName, true)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ FALSE => IsThisGlobalNotEqualToBooleanForCheck(thisName, false)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ LITERAL(str) => IsThisGlobalNotEqualToLiteralForCheck(thisName, str)
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGlobalNotEqualToNumberForCheck(thisName, value, getTypeForField(thisName))

    }| referenceCount ~ NOTEQUAL ~ numeric ^^ {
      case referenceCount ~ _ ~ NUMBER(value) => IsNotEqualsAnyFunctionWithType(referenceCount._1, value, referenceCount._2)

    } | WILLBEMIGRATED ~ NOTEQUAL ~ (TRUE | FALSE) ^^ {
      case _ ~ _ ~ TRUE => WillBeMigratedNotEqualsBoolean(true)
      case _ ~ _ ~ FALSE => WillBeMigratedNotEqualsBoolean(false)
    }

    //** greater
    val greater = (thisIdentifier | thisGlobalIdentifier) ~ GREATERTHAN ~ thatExpressionFunctions  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ value =>
        IsThisGreaterThanAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ value =>
        IsGlobalThisGreaterThanAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))

    } | thatExpressionFunctions ~ GREATERTHAN ~ (numeric) ^^ {
      case thatFuncValue ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeGreaterThanValue(thatFuncValue._1, value, getTypeForField(thatFuncValue._2))

    } | thisIdentifier ~ GREATERTHAN ~ (numeric) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGreaterForCheck(thisName, value, getTypeForField(thisName))

    } | thisGlobalIdentifier ~ GREATERTHAN ~ (numeric) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGlobalGreaterForCheck(thisName, value, getTypeForField(thisName))

    } | referenceCount ~ GREATERTHAN ~ numeric ^^ {
      case referenceCount ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeGreaterThanValue(referenceCount._1, value, referenceCount._2)
    }

    //** greaterEquals
    val greaterEquals = (thisIdentifier | thisGlobalIdentifier) ~ GREATEROREQUAL ~ thatExpressionFunctions  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ value =>
        IsThisGreaterThanEqualAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ value =>
        IsGlobalThisGreaterThanEqualAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))

    } | thisIdentifier ~ GREATEROREQUAL ~ (numeric) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGreaterOrEqualForCheck(thisName, value, getTypeForField(thisName))

    } | thisGlobalIdentifier ~ GREATEROREQUAL ~ (numeric) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGlobalGreaterOrEqualForCheck(thisName, value, getTypeForField(thisName))

    } | (mostRecentFunc | firstFunc | lastFunc | anyOneFunc ) ~ GREATEROREQUAL ~ (numeric) ^^ {
      case thatFuncValue ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeGreaterOrEqualsValue(thatFuncValue._1, value, getTypeForField(thatFuncValue._2))

    } | referenceCount ~ GREATEROREQUAL ~ numeric ^^ {
      case referenceCount ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeGreaterOrEqualsValue(referenceCount._1, value, referenceCount._2)
    }

    //** less
    val less =  (thisIdentifier | thisGlobalIdentifier) ~ LESSTHAN ~ thatExpressionFunctions  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ value =>
        IsThisLessThanAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ value =>
        IsGlobalThisLessThanAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))

    } | thatExpressionFunctions ~ LESSTHAN ~ (numeric) ^^ {
      case thatFuncValue ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeLessThanValue(thatFuncValue._1, value, getTypeForField(thatFuncValue._2))

    } | thisIdentifier ~ LESSTHAN ~ (numeric) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisLessForCheck(thisName, value, getTypeForField(thisName))

    } | thisGlobalIdentifier ~ LESSTHAN ~ (numeric) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGlobalLessForCheck(thisName, value, getTypeForField(thisName))

    } | referenceCount ~ LESSTHAN ~ numeric ^^ {
    case referenceCount ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeLessThanValue(referenceCount._1, value, referenceCount._2)
   }

    //** lessEquals
    val lessEquals =  (thisIdentifier | thisGlobalIdentifier) ~ LESSOREQUAL ~ thatExpressionFunctions  ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ (value) =>
        IsThisLessThanEqualAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ value =>
        IsGlobalThisLessThanEqualAnyFunctionWithType(thisName, value._1, getAndCompareThisThatFieldTypes(value._2, thisName))

    } | thatExpressionFunctions ~ LESSOREQUAL ~ (numeric) ^^ {
      case thatFuncValue ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeLessOrEqualThanValue(thatFuncValue._1, value, getTypeForField(thatFuncValue._2))

    } | thisIdentifier ~ LESSOREQUAL ~ (numeric) ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisLessOrEqualForCheck(thisName, value, getTypeForField(thisName))

    } | thisGlobalIdentifier ~ LESSOREQUAL ~ (numeric) ^^ {
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ NUMBER(value) => IsThisGlobalLessOrEqualForCheck(thisName, value, getTypeForField(thisName))

    } | referenceCount ~ LESSOREQUAL ~ numeric ^^ {
      case referenceCount ~ _ ~ NUMBER(value) => IsAnyFunctionWithTypeLessOrEqualThanValue(referenceCount._1, value, referenceCount._2)
    }

    // TODO: global combination
    val in = (thisIdentifier | thisGlobalIdentifier) ~ IN  ~ LEFTPARENTHESIS ~ (thatIdentifier | thatGlobalIdentifier) ~ RIGHTPARENTHESIS ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => IsThisExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => IsThisExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISIDENTIFIER(thisName) ~ _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => IsThisExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => IsThisExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | (TRUE | FALSE  | numeric | literal) ~ IN ~ external ~ FILTER ~ LEFTPARENTHESIS ~ fieldMap ~ RIGHTPARENTHESIS ~ DOT ~ selectField ^^ {
      case TRUE ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsInExternalTable(true, tableName, fieldName, map)
      case FALSE ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsInExternalTable(false, tableName, fieldName, map)
      case NUMBER(num) ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsInExternalTable(num, tableName, fieldName, map)
      case LITERAL(str) ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsInExternalTable(str, tableName, fieldName, map)

    }

    val inList = thatExpressionFunctions ~ inListSeq  ^^ {
      case thatValue ~ INLIST(list) => IsThatValueInTheList(thatValue._1, list, getTypeForField(thatValue._2))
    }

    // TODO: global combination
    val notIn = (thisIdentifier | thisGlobalIdentifier) ~ NOTIN  ~ LEFTPARENTHESIS ~ (thatIdentifier | thatGlobalIdentifier) ~ RIGHTPARENTHESIS ^^ {
      case THISIDENTIFIER(thisName) ~ _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => IsThisNotExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISIDENTIFIER(thisName) ~ _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => IsThisNotExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ _ ~ THATIDENTIFIER(thatName) ~ _ => IsThisNotExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))
      case THISGLOBALIDENTIFIER(thisName) ~ _ ~ _ ~ THATGLOBALIDENTIFIER(thatName) ~ _ => IsThisNotExistsInThatEvent(thisName, thatName, getAndCompareThisThatFieldTypes(thisName, thatName))

    } | (TRUE | FALSE  | numeric | literal) ~ NOTIN ~ external ~ FILTER ~ LEFTPARENTHESIS ~ fieldMap ~ RIGHTPARENTHESIS ~ DOT ~ selectField ^^ {
      case TRUE ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsNotInExternalTable(true, tableName, fieldName, map)
      case FALSE ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsNotInExternalTable(false, tableName, fieldName, map)
      case NUMBER(num) ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsNotInExternalTable(num, tableName, fieldName, map)
      case LITERAL(str) ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => IsNotInExternalTable(str, tableName, fieldName, map)

    }

    val notInList = thatExpressionFunctions ~ notInListSeq  ^^ {
      case thatValue ~ NOTINLIST(list) => IsThatValueNotInTheList(thatValue._1, list, getTypeForField(thatValue._2))
    }

    val functionExp = thatExpressionFunctions ~ NOTIN ~ external ~ FILTER ~ LEFTPARENTHESIS ~ fieldMap ~ RIGHTPARENTHESIS ~ DOT ~ selectField ^^ {
      case thatFuncValue ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => MostRecentIsNotInExternalTable(thatFuncValue._1, tableName, fieldName, map)
    } | thatExpressionFunctions ~ IN ~ external ~ FILTER ~ LEFTPARENTHESIS ~ fieldMap ~ RIGHTPARENTHESIS ~ DOT ~ selectField ^^ {
      case thatFuncValue ~ _ ~ EXTERNALTABLE(tableName) ~ _ ~ _ ~ map ~ _ ~ _ ~ SELECT(fieldName) => MostRecentIsInExternalTable(thatFuncValue._1, tableName, fieldName, map)
    }

   /* val booleanValue =  (TRUE | FALSE)   ^^ {
      case TRUE => GetBooleanValue(true)
      case FALSE => GetBooleanValue(false)
    }*/

    equals | notEquals | greaterEquals | greater | lessEquals | less | notInList | notIn | inList | in | functionExp

  }

  // TODO Add Variables names to a set
  // TODO Change Lit to take any type - integer nad string

  private def external: Parser[EXTERNALTABLE] = positioned {
    accept("externalTable", { case id @ EXTERNALTABLE(name) => id })
  }

  private def selectField: Parser[SELECT] = positioned {
    accept("selectField", { case id @ SELECT(name) => id })
  }

  private def identifier: Parser[IDENTIFIER] = positioned {
    accept("identifier", { case id @ IDENTIFIER(name) => id })
  }


  private def thisIdentifier: Parser[THISIDENTIFIER] = positioned {
    accept("thisIdentifier", { case id @ THISIDENTIFIER(name) => id })
  }

  private def thisGlobalIdentifier: Parser[THISGLOBALIDENTIFIER] = positioned {
    accept("thisGlobalIdentifier", { case id @ THISGLOBALIDENTIFIER(name) => id })
  }

  private def thatIdentifier: Parser[THATIDENTIFIER] = positioned {
    accept("thatIdentifier", { case id @ THATIDENTIFIER(name) => id })
  }

  private def thatGlobalIdentifier: Parser[THATGLOBALIDENTIFIER] = positioned {
    accept("thatIdentifier", { case id @ THATGLOBALIDENTIFIER(name) => id })
  }


  private def literal: Parser[LITERAL] = positioned {
    accept("string literal", { case lit @ LITERAL(name) => lit })
  }

  private def numeric: Parser[NUMBER] = positioned {
    accept("numeric", { case lit @ NUMBER(name) => lit })
  }

  private def valueSequence: Parser[SEQUENCE] = positioned {
    accept("seq", { case lit @ SEQUENCE(seqList) => lit })
  }

  private def inListSeq: Parser[INLIST] = positioned {
    accept("seq", { case lit @ INLIST(seqList) => lit })
  }

  private def notInListSeq: Parser[NOTINLIST] = positioned {
    accept("seq", { case lit @ NOTINLIST(seqList) => lit })
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
      {
        logger.error("This and That type do not match in domain dictionary for fields: " + name1 + ":" +t1+ " " + name2 + ":"+ t2 + " ,using String type")
        DomainDictionaryInternal.StringType
      }
    // TODO: Error or Exception and break?
  }
}
