package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap

import scala.util.parsing.input.Positional

/**
 * Trigger Expression AST Nodes
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step3: Build an Abstract Syntax Tree. Adding nodes and evaluate methods
 */
sealed abstract class TriggerAST extends Positional {
  val node = new TriggerASTNode(None, None, this, "")
  def eval( event: TaggedCuratedEventWithMap): Boolean
}

class TriggerASTNode(val left: Option[TriggerASTNode], val right: Option[TriggerASTNode], val value: TriggerAST, val code: String) extends Serializable


case class Conjunction(expr1: BooleanExpression, expr2: BooleanExpression) extends BooleanExpression {
  private val code = expr1.node.code + " && " + expr2.node.code
  override val node = new TriggerASTNode( Some(expr1.node), Some(expr2.node), this, code)

  override def eval( event: TaggedCuratedEventWithMap): Boolean = expr1.eval(event) && expr2.eval(event)
}
case class Disjunction(expr1: BooleanExpression, expr2: BooleanExpression) extends BooleanExpression {
  private val code = expr1.node.code + " || " + expr2.node.code
  override val node = new TriggerASTNode(Some(expr1.node), Some(expr2.node), this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = expr1.eval(event) || expr2.eval(event)

}

sealed abstract class BooleanExpression extends TriggerAST {

}

/**
 * anyEvent
 */
case class anyEvent() extends BooleanExpression {
  private val code = "any"
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = true
}
/**
 * DoesExist
 * @param factName
 */
case class DoesExist(factName: String) extends BooleanExpression {
  private val code = "exist(" + factName +")"
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    ASTUtil.doesTermExistInTaggedCuratedEvent(event,factName)
  }
}
case class DoesExistInGlobal(factName: String) extends BooleanExpression {
  private val code = "existInGlobal(" + factName +")"
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    ASTUtil.doesGlobalTermExist(event,factName)
  }
}

/**
 * ISEQUAL
 * @param factNameLHS
 * @param factNameRHS
 */
case class IsThisEqualToThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsThisEqualToGlobalThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsGlobalThisEqualToThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsBothGlobalThisEqualToThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsThisEqualToLiteral(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
     val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
     val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsThisGlobalEqualToLiteral(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsThisEqualToBoolean(factNameLHS: String, factNameRHS: Boolean) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap ): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
   // println("rhs: " + rhs)
    rhs==lhs
  }
}
case class IsThisGlobalEqualToBoolean(factNameLHS: String, factNameRHS: Boolean) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
   // println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs==lhs
  }
}

case class IsThisEqualToNumber(factNameLHS: String, factNameRHS: Number, termType: String ) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
  val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
  val rhs = factNameRHS
  //println("lhs: " + lhs)
  //println("rhs: " + rhs)
    ASTUtil.isEqual(lhs, rhs, termType)
}
}

case class IsThisGlobalEqualToNumber(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isEqual(lhs, rhs, termType)
  }
}

/**
 * ISNOTEQUAL
 * @param factNameLHS
 * @param factNameRHS
 */

case class IsThisNotEqualToThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThisNotEqualToGlobalThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsGlobalThisNotEqualToThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsBothGlobalThisNotEqualToThis(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameRHS)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThisNotEqualToLiteral(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
   // println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThisGlobalNotEqualToLiteral(factNameLHS: String, factNameRHS: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
   // println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThisNotEqualToBoolean(factNameLHS: String, factNameRHS: Boolean) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
   // println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs!=lhs
  }
}
case class IsThisGlobalNotEqualToBoolean(factNameLHS: String, factNameRHS: Boolean) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs!=lhs
  }
}
case class IsThisNotEqualToNumber(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs!=lhs
  }
}
case class IsThisGlobalNotEqualToNumber(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs!=lhs
  }
}

/**
 * ISGREATER
 * @param factNameLHS
 * @param factNameRHS
 */
case class IsThisGreater(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isGreaterThan(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalGreater(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isGreaterThan(lhs, factNameRHS, termType)
  }
}

case class IsThisGreaterOrEqual(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " >= " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isGreaterThanOrEquals(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalGreaterOrEqual(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " >= " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isGreaterThanOrEquals(lhs, factNameRHS, termType)
  }
}

case class IsThisGreaterThanThis(factNameLHS: String, factNameRHS: String, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}
case class IsThisGreaterThanGlobalThis(factNameLHS: String, factNameRHS: String, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameRHS)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}
case class IsThisGlobalGreaterThanThis(factNameLHS: String, factNameRHS: String, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

case class IsThisGreaterOrEqualThis(factNameLHS: String, factNameRHS: String, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " >= " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

case class IsThisGlobalGreaterOrEqualThis(factNameLHS: String, factNameRHS: String, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " >= " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameRHS)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

/**
 * ISLESS
 * @param factNameLHS
 * @param factNameRHS
 */
//todo number get actual Int, long, double from domain dictionary
case class IsThisLess(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " < " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isLessThan(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalLess(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " < " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isLessThan(lhs, factNameRHS, termType)
  }
}

case class IsThisLessOrEqual(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " <= " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isLessThanOrEquals(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalLessOrEqual(factNameLHS: String, factNameRHS: Number, termType: String) extends BooleanExpression {
  private val code = factNameLHS + " <= " + factNameRHS
  override val node = new TriggerASTNode(None, None, this, code)
  override def eval(event: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(event,factNameLHS)
    ASTUtil.isLessThanOrEquals(lhs, factNameRHS, termType)
  }
}



/* "exists(this.isCredit) && this.isCredit == true"
 *
 * class IsCreditAccountBlockedConsistency extends ConsistencyCheck {
override def trigger(e: ParserTaggedCuratedEvent): Boolean = {
doesTermExist(e, "", "isCredit") && getValueFromConsistencyParserEvent(e, "", "isCredit", "Boolean", None) == true
}
And(exists(this.isCredit), Equals(this.isCredit, true))

*/