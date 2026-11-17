package com.experian.edf.oxygen.acceptance.engines.dsl

import com.experian.edf.oxygen.configuration.configs.AcceptanceCondition
import com.experian.edf.oxygen.acceptance.util.QualityScoreParser


/**
 * Utility class to handle compilation and execution of acceptance statements.
 */
class AcceptanceEvaluator {

  var parser: Option[AcceptanceParser] = None

  def apply(conditionConfig: Map[String, AcceptanceCondition], lookupConfig: Map[String, Int]): Unit = {
    parser = Some(new AcceptanceParser(conditionConfig, lookupConfig))
  }

  /**
   * A Compiler with basic lexer and parser invocation interface.
   *
   * @param statement A string to compile.
   * @return Optional first statement of compiled output.
   */
  def compile(statement: String): Option[AcceptanceStatement] = {
    val tokens = AcceptanceLexer(statement)
    if (tokens.isEmpty)  None
    else {
      parser.get(tokens.get)
    }
  }

  /**
   * A basic interface for executing compiled statement.
   *
   * @param root First statement for execution.
   * @param tokenList Reference list of all named high level conditions defined in configuration.
   * @param executor An evaluator for the expressions.
   * @return Result of the execution as tuple of Boolean result and a list of partial results.
   */
  def execute(root: AcceptanceStatement, tokenList: List[AcceptanceCondition], executor: QualityScoreParser): (Boolean, List[AcceptanceCondition]) = {
    root.node.value.evaluateNamed(tokenList, executor)
  }
}
