package com.experian.edf.oxygen.acceptance.engines.dsl

import com.experian.edf.oxygen.configuration.configs.AcceptanceCondition
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import com.experian.edf.oxygen.acceptance.util.QualityScoreParser

import scala.collection.mutable.ListBuffer
import scala.util.parsing.input.Positional
import scala.util.{Failure, Success, Try}

/**
 * Basic trait for Acceptance Condition evaluation.
 *
 */
sealed trait AcceptanceAST extends Positional

/**
 * Base class for Acceptance statement.
 *
 */
abstract class AcceptanceStatement extends AcceptanceAST {
  val node = new AstNode(None, None, this)
  sealed trait CANONICALS
  case object SUM extends CANONICALS
  case object PROD extends CANONICALS

  /**
   * Method to call for evaluating named high level conditions.
   *
   * @param namedExpression Reference list of all named high level conditions defined in configuration.
   * @param evaluator An evaluator for the expressions.
   * @return A tuple with Boolean result and list of partial results with reason codes.
   */
  def evaluateNamed(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser): (Boolean, List[AcceptanceCondition])
}

/**
 * Class encapsulating a tree node.
 *
 * @param left Left subtree
 * @param right Right subtree
 * @param value Value of the node
 */
class AstNode(val left: Option[AstNode], val right: Option[AstNode], val value: AcceptanceStatement) extends Serializable

/**
 * Class representing a Canonical Acceptance Statement.
 *
 * @param expr List of Acceptance Conditions that are referred to in the statement.
 */
abstract class CanonicalStatement(expr: List[AcceptanceCondition]) extends AcceptanceStatement {
  @transient protected lazy val logger: Logger = getLogger(this.getClass)

  def sumOf(bool1: Boolean, bool2: Boolean): Boolean = bool1 || bool2
  def productOf(bool1: Boolean, bool2: Boolean): Boolean = bool1 && bool2
  def evaluateNamedInternal(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser, operator: CANONICALS): (Boolean, List[AcceptanceCondition]) = {
    def combiner(x1: Boolean, x2: Boolean): Boolean = operator match {
      case SUM => sumOf(x1, x2)
      case PROD => productOf(x1, x2)
    }
    var result = operator match {
      case SUM => false
      case PROD => expr.nonEmpty //For PROD if empty return false by default, else PROD with true initially
    }
    val resultList : ListBuffer[AcceptanceCondition] = ListBuffer.empty
    expr.foreach(cond => {
      val res = Try(evaluator.parseAll(evaluator.boolExpr, cond.expression).get) match {
        case Success(sRes) =>
          sRes
        case Failure(fRes) =>
          logger.error(s"Evaluation of ${cond.name} with expression ${cond.expression} failed. Reason: $fRes. Setting default evaluation to false.")
          false
      }
      resultList.append(AcceptanceCondition(cond.name, cond.expression, cond.reasonCode, cond.isLogged, res))
      result = combiner(result, res)
    })
    (result, resultList.toList)
  }
}

/**
 * anyOf statement is a canonical SUM statement.
 * Example: anyOf(namedCond1, namedCond2, .., namedCondN)
 *
 * @param expr List of Acceptance Conditions that are referred to in the statement.
 */
case class AnyOfStatement(expr: List[AcceptanceCondition]) extends CanonicalStatement(expr) {
  override val node = new AstNode(None, None, this)
  override def evaluateNamed(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser): (Boolean, List[AcceptanceCondition]) = {
    evaluateNamedInternal(namedExpression, evaluator, SUM)
  }
}

/**
 * allOf statement is a canonical PROD statement.
 * Example: allOf(namedCond1, namedCond2, .., namedCondN)
 *
 * @param expr List of Acceptance Conditions that are referred to in the statement.
 */
case class AllOfStatement(expr: List[AcceptanceCondition]) extends CanonicalStatement(expr) {
  override val node = new AstNode(None, None, this)
  override def evaluateNamed(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser): (Boolean, List[AcceptanceCondition]) = {
    evaluateNamedInternal(namedExpression, evaluator, PROD)
  }
}
/**
 * Class representing a generalization of Level In Statements.
 *
 * @param expr List of Acceptance Conditions that are referred to in the statement.
 * @param levelListToCheck List of Acceptance Conditions Severity Levels that are referred to in the has Error level statement.
 * @param levelMap Map of Reason codes to Severity Levels loaded from Lookup tables.
 * @param invert Boolean representing membership or none membership tests. Set to true for hasErrorlevelNotIn.
 */
abstract class LevelInStatement(expr: List[AcceptanceCondition], levelListToCheck: List[Int], levelMap: Map[String, Int], invert: Boolean) extends CanonicalStatement(expr) {
  def evaluateNamedIntermediate(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser, operator: CANONICALS): (Boolean, List[AcceptanceCondition]) = {
    val (_, partResultList: List[AcceptanceCondition]) = evaluateNamedInternal(namedExpression, evaluator, operator)
    val levelForCheck: ListBuffer[Int] = ListBuffer.empty
    partResultList.foreach(partResult => {
      if (partResult.reasonCode.isEmpty || !levelMap.contains(partResult.reasonCode)) {
        logger.error(s"Unable to lookup reason code ${partResult.reasonCode} for condition ${partResult.name}. It will be skipped in severity evaluation.")
      } else {
        if (partResult.result) levelForCheck.append(levelMap(partResult.reasonCode))
      }
    })
    val levelSet = levelForCheck.toList.toSet
    val noIntersection = levelSet.intersect(levelListToCheck.toSet).isEmpty
    val result = if (invert) noIntersection else !noIntersection
    (result, partResultList)
  }
}

/**
 * A canonical SUM statement with optional severity level checks.
 * Examples:
 * anyOf(namedCond1, namedCond2,..,namedCondN) hasErrorlevelIn(value1, value2.., valueN)
 * anyOf(namedCond1, namedCond2,..,namedCondN) hasErrorlevelNotIn(value1, value2.., valueN)
 *
 * @param expr List of Acceptance Conditions that are referred to in the anyOf statement.
 * @param levelListToCheck List of Acceptance Conditions Severity Levels that are referred to in the has Error level statement.
 * @param levelMap Map of Reason codes to Severity Levels loaded from Lookup tables.
 * @param invert Boolean representing membership or none membership tests. Set to true for hasErrorlevelNotIn.
 */
case class AnyOfLevelInStatement(expr: List[AcceptanceCondition], levelListToCheck: List[Int], levelMap: Map[String, Int], invert: Boolean) extends LevelInStatement(expr, levelListToCheck, levelMap, invert) {
  override val node = new AstNode(None, None, this)
  override def evaluateNamed(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser): (Boolean, List[AcceptanceCondition]) = {
    evaluateNamedIntermediate(namedExpression, evaluator, SUM)
  }
}

/**
 * A canonical PROD statement with optional severity level checks.
 * Examples:
 * allOf(namedCond1, namedCond2,..,namedCondN) hasErrorlevelIn(value1, value2.., valueN)
 * allOf(namedCond1, namedCond2,..,namedCondN) hasErrorlevelNotIn(value1, value2.., valueN)
 *
 * @param expr List of Acceptance Conditions that are referred to in the allOf statement.
 * @param levelListToCheck List of Acceptance Conditions Severity Levels that are referred to in the has Error level statement.
 * @param levelMap Map of Reason codes to Severity Levels loaded from Lookup tables.
 * @param invert Boolean representing membership or none membership tests. Set to true for hasErrorlevelNotIn.
 */
case class AllOfLevelInStatement(expr: List[AcceptanceCondition], levelListToCheck: List[Int], levelMap: Map[String, Int], invert: Boolean) extends LevelInStatement(expr, levelListToCheck, levelMap, invert) {
  override val node = new AstNode(None, None, this)
  override def evaluateNamed(namedExpression: List[AcceptanceCondition], evaluator: QualityScoreParser): (Boolean, List[AcceptanceCondition]) = {
    evaluateNamedIntermediate(namedExpression, evaluator, PROD)
  }
}
