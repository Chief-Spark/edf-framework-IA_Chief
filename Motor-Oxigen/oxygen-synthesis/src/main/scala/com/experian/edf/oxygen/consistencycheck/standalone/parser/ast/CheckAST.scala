package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.{StateCheck, Util}

import scala.util.parsing.input.Positional

/**
 * Check Expression AST Nodes
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step3: Build an Abstract Syntax Tree. Adding nodes and evaluate methods
 */
sealed abstract class CheckAST extends Positional {
  val node = new CheckASTNode(None, None, this, "")

  def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean

  def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean
}

/**
 * Check Abstract Syntax Tree Node
 *
 * @param left
 * @param right
 * @param value
 * @param code
 */
class CheckASTNode(val left: Option[CheckASTNode], val right: Option[CheckASTNode], val value: CheckAST, val code: String) extends Serializable

/**
 * Abstract Syntax Tree Node for configurable functions such as MostRecent, First, Last etc.
 *
 * @param leftFun
 * @param rightFun
 * @param valueFun
 * @param codeFun
 */
class CheckFunASTNode(val leftFun: Option[CheckASTNode], val rightFun: Option[CheckASTNode], val valueFun: CheckAST, val codeFun: String) extends
  CheckASTNode(leftFun, rightFun, valueFun, codeFun)

/**
 * Conjunction &&: Evaluate functionality for binary expressions combined with an AND operator
 *
 * @param expr1
 * @param expr2
 */
case class CheckConjunction(expr1: CheckBooleanExpression, expr2: CheckBooleanExpression) extends CheckBooleanExpression {
  private val code = expr1.node.code + " && " + expr2.node.code
  override val node = new CheckASTNode(Some(expr1.node), Some(expr2.node), this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean =
    expr1.eval(thisEvent, thatEvent, checkObject, stateCheck) && expr2.eval(thisEvent, thatEvent, checkObject, stateCheck)

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean =
    expr1.evalForNonPeriodic(thisEvent, thatEvent, checkObject) && expr2.evalForNonPeriodic(thisEvent, thatEvent, checkObject)
}

/**
 * Disjunction ||: Evaluate functionality for binary expressions combined with an OR operator
 *
 * @param expr1
 * @param expr2
 */
case class CheckDisjunction(expr1: CheckBooleanExpression, expr2: CheckBooleanExpression) extends CheckBooleanExpression {
  private val code = expr1.node.code + " || " + expr2.node.code
  override val node = new CheckASTNode(Some(expr1.node), Some(expr2.node), this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean =
    expr1.eval(thisEvent, thatEvent, checkObject, stateCheck) || expr2.eval(thisEvent, thatEvent, checkObject, stateCheck)

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = expr1.evalForNonPeriodic(thisEvent, thatEvent, checkObject) || expr2.evalForNonPeriodic(thisEvent, thatEvent, checkObject)

}

sealed abstract class CheckBooleanExpression extends CheckAST {}

sealed abstract class CheckFunctionExpression extends CheckAST {
  def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): (Any) = ???

  def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): (Any) = ???

}

sealed abstract class CheckFunctionExpressionState extends CheckAST {
  def evalFunctionStateCheck(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], stateCheck: StateCheck): (Any) = ???

  def evalFunctionForNonPeriodicStateCheck(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkASTObject: CheckASTObject): (Any) = ???

}

/**
 * MOST RECENT THAT
 *
 * @param thatName
 */
case class MostRecentValue(thatName: String) extends CheckFunctionExpression {
  private val code = "mostRecent(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getMostRecentForTermFromSyntheticEvent(thatName, thatEvents)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getMostRecentForTermFromTaggedCuratedEvent(thatName, thatEvents)
    value
  }

}

case class MostRecentGlobalValue(thatName: String) extends CheckFunctionExpression {
  private val code = "mostRecent(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getMostRecentForGlobalTermFromSyntheticEvent(thatName, thatEvents)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getMostRecentForGlobalTermFromTaggedCuratedEvent(thatName, thatEvents)
    value
  }
}

case class CheckStateValue(fieldName: String, stateId: String, thatName: String) extends CheckFunctionExpressionState {
  private val code = SynthesisConstants.SHOULD_ACCEPT_STATE + "(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunctionStateCheck(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], stateCheck: StateCheck): Any = {
    //    val value = if (thatEvents == null || thatEvents.isEmpty ) null else ASTUtil.checkStateOpen(fieldName, stateId, thatName, thisEvent, stateCheck)
//    val value = if (thatEvents == null || thatEvents.isEmpty) true else ASTUtil.checkStateOpen(fieldName, stateId, thatName, thisEvent, stateCheck)
    val value = ASTUtil.checkStateOpen(fieldName, stateId, thatName, thisEvent, stateCheck)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???
}

case class CheckStateGlobalValue(thatName: String) extends CheckFunctionExpression {
  private val code = SynthesisConstants.SHOULD_ACCEPT_STATE + "(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvents.last, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvents.last, thatName)
    value
  }
}

/**
 * LAST THAT
 *
 * @param thatName
 */
case class LastValue(thatName: String) extends CheckFunctionExpression {
  private val code = "last(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvents.last, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvents.last, thatName)
    value
  }
}

case class LastGlobalValue(thatName: String) extends CheckFunctionExpression {
  private val code = "last(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvents.last, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvents.last, thatName)
    value
  }


}

/**
 * First THAT
 *
 * @param thatName
 */
case class FirstValue(thatName: String) extends CheckFunctionExpression {
  private val code = "first(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvents.head, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvents.head, thatName)
    value
  }

}

case class FirstGlobalValue(thatName: String) extends CheckFunctionExpression {
  private val code = "first(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvents.head, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvents.head, thatName)
    value
  }
}

/**
 * AnyOne THAT
 *
 * @param thatName
 */
case class AnyOneValue(thatName: String) extends CheckFunctionExpression {
  private val code = "anyOne(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvents.head, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvents.head, thatName)
    value
  }

}

case class AnyOneGlobalValue(thatName: String) extends CheckFunctionExpression {
  private val code = "anyOne(" + thatName + ")"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvents.head, thatName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = if (thatEvents == null || thatEvents.isEmpty) null else ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvents.head, thatName)
    value
  }
}

/**
 * ReferenceEventCount THAT
 */

case class ReferenceEventCountValue() extends CheckFunctionExpression {
  private val code = "referenceEventCount"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = thatEvents.size
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = thatEvents.size
    value
  }
}

/**
 * GET THAT VALUE
 */

case class GetThisTermValue(thisName: String) extends CheckFunctionExpression {
  private val code = "termValue"
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = ???

  override def evalFunction(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent]): Any = {
    val value = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???

  override def evalFunctionForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    val value = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    value
  }
}

/**
 * getBoolValue
 *
 * @param value
 */
case class GetBooleanValue(value: Boolean) extends CheckBooleanExpression {
  private val code = value.toString
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    value
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    value
  }
}

/**
 * WillBeMigrated
 */

case class WillBeMigratedEqualsBoolean(rhsValue: Boolean) extends CheckBooleanExpression {
  private val code = "willBeMigrated" + "==" + rhsValue
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val trackingKey = Util.getSRKey(thisEvent.subscriber, thisEvent.subscriberRelation)
    val willBeMigrated = checkObject.relationshipMigrationTracker.contains(trackingKey)
    willBeMigrated == rhsValue
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val trackingKey = Util.getSRKey(thisEvent.subscriber, thisEvent.subscriberRelation)
    val willBeMigrated = checkObject.relationshipMigrationTracker.contains(trackingKey)
    willBeMigrated == rhsValue
  }
}

case class WillBeMigratedNotEqualsBoolean(rhsValue: Boolean) extends CheckBooleanExpression {
  private val code = "willBeMigrated" + "!=" + rhsValue
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val trackingKey = Util.getSRKey(thisEvent.subscriber, thisEvent.subscriberRelation)
    val willBeMigrated = checkObject.relationshipMigrationTracker.contains(trackingKey)
    willBeMigrated != rhsValue
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val trackingKey = Util.getSRKey(thisEvent.subscriber, thisEvent.subscriberRelation)
    val willBeMigrated = checkObject.relationshipMigrationTracker.contains(trackingKey)
    willBeMigrated != rhsValue
  }
}

/**
 * IN EXTERNAL TABLE
 *
 * @param value
 * @param tableName
 * @param selectField
 * @param fieldMap
 */
case class IsInExternalTable(value: Any, tableName: String, selectField: String, fieldMap: Map[String, CheckFunctionExpression]) extends CheckBooleanExpression {
  private val code = value + " in " + tableName
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunction(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    rhsList.contains(value)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunctionForNonPeriodic(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    rhsList.contains(value)
  }
}

/**
 * NOT IN EXTERNAL TABLE
 *
 * @param value
 * @param tableName
 * @param selectField
 * @param fieldMap
 */
case class IsNotInExternalTable(value: Any, tableName: String, selectField: String, fieldMap: Map[String, CheckFunctionExpression]) extends CheckBooleanExpression {
  private val code = value + " notIn " + tableName
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunction(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    !rhsList.contains(value)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunctionForNonPeriodic(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    !rhsList.contains(value)

  }
}
case class MostRecentIsNotInExternalTable(functionExpression: CheckFunctionExpression, tableName: String, selectField: String, fieldMap: Map[String, CheckFunctionExpression]) extends CheckBooleanExpression {
  private val code = functionExpression + " notIn " + tableName
  override val node = new CheckASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val value = functionExpression.evalFunction(thisEvent, thatEvents)
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunction(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    !rhsList.contains(value)
  }
  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val value = functionExpression.evalFunctionForNonPeriodic(thisEvent, thatEvents)
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunctionForNonPeriodic(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    !rhsList.contains(value)
  }
}
case class MostRecentIsInExternalTable(functionExpression: CheckFunctionExpression, tableName: String, selectField: String, fieldMap: Map[String, CheckFunctionExpression]) extends CheckBooleanExpression {
  private val code = functionExpression + " in " + tableName
  override val node = new CheckASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val value = functionExpression.evalFunction(thisEvent, thatEvents)
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunction(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    rhsList.contains(value)
  }
  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val value = functionExpression.evalFunctionForNonPeriodic(thisEvent, thatEvents)
    val fieldMapExpand = fieldMap.map(x => (x._1, x._2.evalFunctionForNonPeriodic(thisEvent, thatEvents).toString))
    val rhsList = ASTUtil.getExternalStatus(tableName, fieldMapExpand, selectField, checkObject.lookupService)
    rhsList.contains(value)
  }
}


/**
 * THIS EXISTS IN THAT FUNCTION VALUE
 *
 * @param thisName
 * @param thatName
 * @param termType
 */
case class IsThisExistsInThatEvent(thisName: String, thatName: String, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " in " + thatName
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val thisTermValue = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    if (thatEvents.find(x => {
      val thatTermValue = ASTUtil.getAnyValueForTermFromSyntheticEvent(x, thatName)
      ASTUtil.isEqual(thatTermValue, thisTermValue, termType)
    }).orNull == null) false else true
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val thisTermValue = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    if (thatEvents.find(x => {
      val thatTermValue = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(x, thatName)
      ASTUtil.isEqual(thatTermValue, thisTermValue, termType)
    }).orNull == null) false else true
  }
}

/**
 * THAT VALUE IN THE LIST
 *
 * @param thatValue
 * @param valueList
 * @param termType
 */
case class IsThatValueInTheList(thatValue: CheckFunctionExpression, valueList: List[Any], termType: String) extends CheckBooleanExpression {
  private val code = thatValue + " in " + valueList.toString()
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val value = thatValue.evalFunction(thisEvent, thatEvents)
    valueList.contains(value)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val value = thatValue.evalFunctionForNonPeriodic(thisEvent, thatEvents)
    valueList.contains(value)
  }
}

/**
 * THAT VALUE NOT IN THE LIST
 *
 * @param thatValue
 * @param valueList
 * @param termType
 */
case class IsThatValueNotInTheList(thatValue: CheckFunctionExpression, valueList: List[Any], termType: String) extends CheckBooleanExpression {
  private val code = thatValue + " notIn " + valueList.toString()
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val value = thatValue.evalFunction(thisEvent, thatEvents)
    !valueList.contains(value)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val value = thatValue.evalFunctionForNonPeriodic(thisEvent, thatEvents)
    !valueList.contains(value)
  }
}

/**
 * THIS NOT EXISTS IN THAT FUNCTION VALUE
 *
 * @param thisName
 * @param thatName
 * @param termType
 */
case class IsThisNotExistsInThatEvent(thisName: String, thatName: String, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " in " + thatName
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val thisTermValue = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    if (thatEvents.find(x => {
      val thatTermValue = ASTUtil.getAnyValueForTermFromSyntheticEvent(x, thatName)
      ASTUtil.isEqual(thatTermValue, thisTermValue, termType)
    }).orNull == null) true else false
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val thisTermValue = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    if (thatEvents.find(x => {
      val thatTermValue = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(x, thatName)
      ASTUtil.isEqual(thatTermValue, thisTermValue, termType)
    }).orNull == null) true else false
  }
}

/**
 * THIS EQUALS THAT FUNCTION VALUE
 *
 * @param thisName
 * @param value
 * @param termType
 */
case class IsThisEqualsAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " == " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    if (rhs == null) true else ASTUtil.isEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    if (rhs == null) true else ASTUtil.isEqual(lhs, rhs, termType)
  }
}

case class IsGlobalThisEqualsAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " == " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isEqual(lhs, rhs, termType)
  }
}

/**
 * THAT FUNCTION EXPRESSION EQUAL TO VALUE
 *
 * @param lhsvalue
 * @param rhs
 * @param termType
 */
case class IsEqualsAnyFunctionWithType(lhsvalue: CheckFunctionExpression, rhs: Any, termType: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " == " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isEqual(lhs, rhs, termType)
  }

}

case class IsEqualsAnyFunctionToLiteral(lhsvalue: CheckFunctionExpression, rhs: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " == " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.toString == rhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.toString == rhs
  }
}

case class IsEqualsAnyFunctionToBoolean(lhsvalue: CheckFunctionExpression, rhs: Boolean) extends CheckBooleanExpression {
  private val code = lhsvalue + " == " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.asInstanceOf[Boolean] == rhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.asInstanceOf[Boolean] == rhs
  }
}

case class IsEqualsAnyFunctionToBooleanStateCheck(lhsvalue: CheckFunctionExpressionState, rhs: Boolean) extends CheckBooleanExpression {
  private val code = lhsvalue + " == " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunctionStateCheck(thisEvent, thatEvent, stateCheck)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.asInstanceOf[Boolean] == rhs
  }

  //  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap],checkObject: CheckASTObject): Boolean = {
  //    val lhs = lhsvalue.evalFunctionForNonPeriodicStateCheck(thisEvent,thatEvent,checkObject)
  //    //println("lhs:" + lhs + "rhs:" + rhs)
  //    /* if ( lhs == null) true else */  lhs.asInstanceOf[Boolean] == rhs
  //  }
  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = ???
}

/**
 * THIS NOT EQUALS THAT FUNCTION VALUE
 *
 * @param thisName
 * @param value
 * @param termType
 */
case class IsThisNotEqualsAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " != " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isNotEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isNotEqual(lhs, rhs, termType)
  }
}

case class IsGlobalThisNotEqualsAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " != " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isNotEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isNotEqual(lhs, rhs, termType)
  }
}

/**
 * THAT FUNCTION EXPRESSION NOT EQUAL TO VALUE
 *
 * @param lhsvalue
 * @param rhs
 * @param termType
 */
case class IsNotEqualsAnyFunctionWithType(lhsvalue: CheckFunctionExpression, rhs: Any, termType: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " != " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /*/* if ( lhs == null) true else */ */ ASTUtil.isNotEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /*/* if ( lhs == null) true else */*/ ASTUtil.isNotEqual(lhs, rhs, termType)
  }
}

case class IsNotEqualsAnyFunctionToLiteral(lhsvalue: CheckFunctionExpression, rhs: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " != " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.toString != rhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.toString != rhs
  }
}

case class IsNotEqualsAnyFunctionToBoolean(lhsvalue: CheckFunctionExpression, rhs: Boolean) extends CheckBooleanExpression {
  private val code = lhsvalue + " != " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.asInstanceOf[Boolean] != rhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ lhs.asInstanceOf[Boolean] != rhs
  }
}

/**
 * THIS GREATER THAN THAT FUNCTION VALUE
 *
 * @param thisName
 * @param value
 * @param termType
 */
case class IsThisGreaterThanAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " > " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThan(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

case class IsGlobalThisGreaterThanAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " > " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThan(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

/**
 * THAT FUNCTION EXPRESSION GREATER THAN VALUE
 *
 * @param lhsvalue
 * @param rhs
 * @param termType
 */
case class IsAnyFunctionWithTypeGreaterThanValue(lhsvalue: CheckFunctionExpression, rhs: Any, termType: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " > " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isGreaterThan(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

/**
 * THIS GREATER THAN EQUALS THAT FUNCTION VALUE
 *
 * @param thisName
 * @param value
 * @param termType
 */
case class IsThisGreaterThanEqualAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " >= " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

case class IsGlobalThisGreaterThanEqualAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " >= " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

/**
 * THAT FUNCTION EXPRESSION GREATER OR EQUALS VALUE
 *
 * @param lhsvalue
 * @param rhs
 * @param termType
 */
case class IsAnyFunctionWithTypeGreaterOrEqualsValue(lhsvalue: CheckFunctionExpression, rhs: Any, termType: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " >= " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

/**
 * THIS LESSER THAN THAT FUNCTION VALUE
 *
 * @param thisName
 * @param value
 * @param termType
 */
case class IsThisLessThanAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " < " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThan(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

case class IsGlobalThisLessThanAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " < " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThan(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

/**
 * THAT FUNCTION EXPRESSION LESS THAN VALUE
 *
 * @param lhsvalue
 * @param rhs
 * @param termType
 */
case class IsAnyFunctionWithTypeLessThanValue(lhsvalue: CheckFunctionExpression, rhs: Any, termType: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " < " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isLessThan(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

/**
 * THIS LESSER or EQUAL THAN THAT FUNCTION VALUE
 *
 * @param thisName
 * @param value
 * @param termType
 */
case class IsThisLessThanEqualAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " <= " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

case class IsGlobalThisLessThanEqualAnyFunctionWithType(thisName: String, value: CheckFunctionExpression, termType: String) extends CheckBooleanExpression {
  private val code = thisName + " <= " + value
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunction(thisEvent, thatEvent)
    // println("lhs:",lhs," rhs:",rhs)
    if (rhs == null) true else ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thisName)
    val rhs = value.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    if (rhs == null) true else ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

/**
 * THAT FUNCTION EXPRESSION LESS OR EQUAL THAN VALUE
 *
 * @param lhsvalue
 * @param rhs
 * @param termType
 */
case class IsAnyFunctionWithTypeLessOrEqualThanValue(lhsvalue: CheckFunctionExpression, rhs: Any, termType: String) extends CheckBooleanExpression {
  private val code = lhsvalue + " <= " + rhs
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = lhsvalue.evalFunction(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = lhsvalue.evalFunctionForNonPeriodic(thisEvent, thatEvent)
    //println("lhs:" + lhs + "rhs:" + rhs)
    /* if ( lhs == null) true else */ ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}


/**
 * IS THIS EQUAL
 */

case class IsThisEqualToLiteralForCheck(factNameLHS: String, factNameRHS: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs == lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs == lhs
  }
}

case class IsThisGlobalEqualToLiteralForCheck(factNameLHS: String, factNameRHS: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs == lhs
  }
}

case class IsThisEqualToBooleanForCheck(factNameLHS: String, factNameRHS: Boolean) extends CheckBooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    //println("lhs: " + lhs)
    // println("rhs: " + rhs)
    rhs == lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs == lhs
  }
}

case class IsThisGlobalEqualToBooleanForCheck(factNameLHS: String, factNameRHS: Boolean) extends CheckBooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    // println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs == lhs
  }
}

case class IsThisEqualToNumberForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    ASTUtil.isEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    ASTUtil.isEqual(lhs, rhs, termType)
  }
}

case class IsThisGlobalEqualToNumberForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " == " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    ASTUtil.isEqual(lhs, rhs, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    ASTUtil.isEqual(lhs, rhs, termType)
  }


}


/**
 * IS THIS NOT EQUAL
 *
 * @param
 */
case class IsThisNotEqualToLiteralForCheck(factNameLHS: String, factNameRHS: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }
}

case class IsThisGlobalNotEqualToLiteralForCheck(factNameLHS: String, factNameRHS: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }
}

case class IsThisNotEqualToBooleanForCheck(factNameLHS: String, factNameRHS: Boolean) extends CheckBooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }
}

case class IsThisGlobalNotEqualToBooleanForCheck(factNameLHS: String, factNameRHS: Boolean) extends CheckBooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }
}

case class IsThisNotEqualToNumberForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }
}

case class IsThisGlobalNotEqualToNumberForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " != " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    val rhs = factNameRHS
    rhs != lhs
  }
}

/**
 * IS THIS GREATER THAN / GREATER EQUALS VALUE
 *
 * @param factNameLHS
 * @param factNameRHS
 */
case class IsThisGreaterForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThan(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThan(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalGreaterForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " > " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThan(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThan(lhs, factNameRHS, termType)
  }
}

case class IsThisGreaterOrEqualForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " >= " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThanOrEquals(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThanOrEquals(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalGreaterOrEqualForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " >= " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThanOrEquals(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isGreaterThanOrEquals(lhs, factNameRHS, termType)
  }
}

/**
 * IS LESS THAN OR LESS THAN EQUALS
 *
 * @param factNameLHS
 * @param factNameRHS
 */
//todo number get actual Int, long, double from domain dictionary
case class IsThisLessForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " < " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThan(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThan(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalLessForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " < " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThan(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThan(lhs, factNameRHS, termType)
  }
}

case class IsThisLessOrEqualForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " <= " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThanOrEquals(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThanOrEquals(lhs, factNameRHS, termType)
  }
}

case class IsThisGlobalLessOrEqualForCheck(factNameLHS: String, factNameRHS: Number, termType: String) extends CheckBooleanExpression {
  private val code = factNameLHS + " <= " + factNameRHS
  override val node = new CheckASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThanOrEquals(lhs, factNameRHS, termType)
  }

  override def evalForNonPeriodic(thisEvent: TaggedCuratedEventWithMap, thatEvent: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, factNameLHS)
    ASTUtil.isLessThanOrEquals(lhs, factNameRHS, termType)
  }
}