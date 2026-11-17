package com.experian.edf.oxygen.acceptance.engines.dsl

import com.experian.edf.oxygen.acceptance.util.OxygenTokenReader
import com.experian.edf.oxygen.configuration.configs.AcceptanceCondition
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable.ListBuffer
import scala.util.parsing.combinator.Parsers

/**
 * A Parser class for Acceptance Tokens to produce Acceptance Statements.
 *
 * @param conditionMap A map of condition name to definition for named conditions.
 * @param lookupMap A map of reason code to severity level for reason codes referred in named conditions.
 */
class AcceptanceParser(conditionMap: Map[String, AcceptanceCondition], lookupMap: Map[String, Int]) extends Parsers with Serializable {
  override type Elem = AcceptanceToken
  @transient private lazy val logger = getLogger(this.getClass)

  def apply(tokens: Seq[AcceptanceToken]): Option[AcceptanceStatement] = {
    val reader = new OxygenTokenReader[AcceptanceToken](tokens)
    statement(reader) match {
      case NoSuccess(str, input) =>
        logger.error(s"Unsuccessful parsing for Acceptance conditions at line ${input.pos.line} column ${input.pos.column}. Message: $str")
        None
      case Success(result, _) => Some(result)
    }
  }

  def statement: Parser[AcceptanceStatement] = positioned {
    anyOfStatement | allOfStatement
  }

  def allOfStatement: Parser[AcceptanceStatement] = allOf ~ opt(hasErrorlevelIn | hasErrorlevelNotIn) ^^ {
      case ALLOF(condList) ~ None => AllOfStatement(getAcceptanceConditions(condList))
      case ALLOF(condList) ~ Some(HASERRORLEVELIN(vallist)) =>
        AllOfLevelInStatement(getAcceptanceConditions(condList), vallist, lookupMap, invert = false)
      case ALLOF(condList) ~ Some(HASERRORLEVELNOTIN(vallist)) =>
        AllOfLevelInStatement(getAcceptanceConditions(condList), vallist, lookupMap, invert = true)
  }

  def anyOfStatement: Parser[AcceptanceStatement] = anyOf ~ opt(hasErrorlevelIn | hasErrorlevelNotIn) ^^ {
    case ANYOF(condList) ~ None => AnyOfStatement(getAcceptanceConditions(condList))
    case ANYOF(condList) ~ Some(HASERRORLEVELIN(vallist)) =>
      AnyOfLevelInStatement(getAcceptanceConditions(condList), vallist, lookupMap, invert = false)
    case ANYOF(condList) ~ Some(HASERRORLEVELNOTIN(vallist)) =>
      AnyOfLevelInStatement(getAcceptanceConditions(condList), vallist, lookupMap, invert = true)
  }

  def anyOf: Parser[ANYOF] = positioned {
    accept("anyof", { case anyof @ ANYOF(_) => anyof })
  }

  def allOf: Parser[ALLOF] = positioned {
    accept("allof", { case allof @ ALLOF(_) => allof })
  }

  def hasErrorlevelIn: Parser[HASERRORLEVELIN] = positioned {
    accept("haserrorlevelin", { case hasErrLevelIn @ HASERRORLEVELIN(_) => hasErrLevelIn })
  }

  def hasErrorlevelNotIn: Parser[HASERRORLEVELNOTIN] = positioned {
    accept("haserrorlevelnotin", { case hasErrLevelNotIn @ HASERRORLEVELNOTIN(_) => hasErrLevelNotIn })
  }

  private def getAcceptanceConditions(names: List[String]): List[AcceptanceCondition] = {
    val (defCondns, undefCondn) = names.partition(x => conditionMap.contains(x))
    if (undefCondn.nonEmpty)
      logger.error(s"Please correct the configuration. Setting evaluation result as false for Undefined conditions: ${undefCondn.mkString(", ")}")
    val validConditions:  ListBuffer[AcceptanceCondition] = ListBuffer.empty
    for (valid <- defCondns) validConditions.append(conditionMap(valid))
    validConditions.toList
  }

}
