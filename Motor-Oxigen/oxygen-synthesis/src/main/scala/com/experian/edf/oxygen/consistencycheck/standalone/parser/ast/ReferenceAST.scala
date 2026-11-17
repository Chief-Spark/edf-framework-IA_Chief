package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}

import scala.util.parsing.input.Positional

/**
 * Reference Expression AST Nodes
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step3: Build an Abstract Syntax Tree. Adding nodes and evaluate methods
 */
sealed abstract class ReferenceAST extends Positional {
  val node = new ReferenceASTNode(None, None, this, "")
  def eval( thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean

  // TODO: find a more approp name
  def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean
}

class ReferenceASTNode (val left: Option[ReferenceASTNode], val right: Option[ReferenceASTNode], val value: ReferenceAST, val code: String) extends Serializable

case class RefConjunction(expr1: RefBooleanExpression, expr2: RefBooleanExpression) extends RefBooleanExpression {
  private val code = expr1.node.code + " && " + expr2.node.code
  override val node = new ReferenceASTNode( Some(expr1.node), Some(expr2.node), this, code)
  override def eval( thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = expr1.eval(thisEvent,thatEvent) && expr2.eval(thisEvent,thatEvent)
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = expr1.evalBothCurated(thisEvent,thatEvent) && expr2.evalBothCurated(thisEvent,thatEvent)
}

case class RefDisjunction(expr1: RefBooleanExpression, expr2: RefBooleanExpression) extends RefBooleanExpression {
  private val code = expr1.node.code + " || " + expr2.node.code
  override val node = new ReferenceASTNode(Some(expr1.node), Some(expr2.node), this, code)
  override def eval( thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = expr1.eval(thisEvent,thatEvent) || expr2.eval(thisEvent,thatEvent)
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = expr1.evalBothCurated(thisEvent,thatEvent) || expr2.evalBothCurated(thisEvent,thatEvent)

}

sealed abstract class RefBooleanExpression extends ReferenceAST {}

/**
 * DoesExist
 * @param factName
 */
case class DoesExistInThis(factName: String) extends RefBooleanExpression {
  private val code = "exist(" + factName +")"
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval( thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean =  {
    ASTUtil.doesTermExistInTaggedCuratedEvent(thisEvent,factName)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    ASTUtil.doesTermExistInTaggedCuratedEvent(thisEvent,factName)
  }
}

case class DoesExistInThat(factName: String) extends RefBooleanExpression {
  private val code = "exist(" + factName +")"
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval( thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean =  {
    ASTUtil.doesTermExistInSyntheticEvent(thatEvent,factName)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    ASTUtil.doesTermExistInTaggedCuratedEvent(thatEvent,factName)
  }
}

case class DoesExistInGlobalRef(factName: String) extends RefBooleanExpression {
  private val code = "existInGlobal(" + factName + ")"
  override val node = new ReferenceASTNode(None, None, this, code)

  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = true
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = true
}

/**
 * ISEQUAL
 * @param thisName
 * @param thatName
 */
case class IsThisEqualToThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " == " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    ////println("rhs: " + rhs)
    rhs == lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
   // println("lhs: " + lhs)
   // //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsThatEqualToBoolean(thatName: String, boolValue: Boolean) extends RefBooleanExpression {
  private val code = thatName + " == " + boolValue
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + boolValue)
    lhs == boolValue
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs == boolValue
  }
}

case class IsThatEqualToLiteral(thatName: String, value: String) extends RefBooleanExpression {
  private val code = thatName + " == " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs == value
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs == value
  }
}

case class IsThatEqualToNumber(thatName: String, value: Number, termType: String ) extends RefBooleanExpression {
  private val code = thatName + " == " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isEqual(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean  = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isEqual(lhs, value, termType)
  }
}

/**
 * ISGLOBALEQUAL
 * @param thisName
 * @param thatName
 */
// TODO: if only one is global ..either this or that..need to be handled
case class IsBothGlobalThisEqualToThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " == " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}
case class IsThisEqualToGlobalThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " == " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsGlobalThisEqualToThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " == " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsThatEqualToGlobalThis(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thatName + " == " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsGlobalThatEqualToThis(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thatName + " == " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs == lhs
  }
}

case class IsGlobalThatEqualToBoolean(thatName: String, boolValue: Boolean) extends RefBooleanExpression {
  private val code = thatName + " == " + boolValue
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs == boolValue
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs == boolValue
  }
}

case class IsGlobalThatEqualToLiteral(thatName: String, value: String) extends RefBooleanExpression {
  private val code = thatName + " == " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs == value
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean =  {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs == value
  }
}

case class IsGlobalThatEqualToNumber(thatName: String, value: Number, termType: String ) extends RefBooleanExpression {
  private val code = thatName + " == " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isEqual(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isEqual(lhs, value, termType)
  }
}

/**
 * ISNOTEQUAL
 * @param thisName
 * @param thatName
 */
case class IsThisNotEqualToThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " != " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThatNotEqualToBoolean(thatName: String, boolValue: Boolean) extends RefBooleanExpression {
  private val code = thatName + " != " + boolValue
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs != boolValue
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs != boolValue
  }
}

case class IsThatNotEqualToLiteral(thatName: String, value: String) extends RefBooleanExpression {
  private val code = thatName + " != " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs != value
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs != value
  }
}

case class IsThatNotEqualToNumber(thatName: String, value: Number, termType: String ) extends RefBooleanExpression {
  private val code = thatName + " != " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isNotEqual(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isNotEqual(lhs, value, termType)
  }
}

/**
 * ISGLOBALNOTEQUAL
 * @param thisName
 * @param thatName
 */
case class IsBothGlobalThisNotEqualToThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " != " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThisNotEqualToGlobalThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " != " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsGlobalThisNotEqualToThat(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thisName + " != " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsThatNotEqualToGlobalThis(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thatName + " != " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}

case class IsGlobalThatNotEqualToThis(thisName: String, thatName: String) extends RefBooleanExpression {
  private val code = thatName + " != " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    //println("lhs: " + lhs)
    //println("rhs: " + rhs)
    rhs != lhs
  }
}


case class IsGlobalThatNotEqualToBoolean(thatName: String, boolValue: Boolean) extends RefBooleanExpression {
  private val code = thatName + " != " + boolValue
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs != boolValue
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
//        println("rhs: " + boolValue)
    lhs != boolValue
  }
}

case class IsGlobalThatNotEqualToLiteral(thatName: String, value: String) extends RefBooleanExpression {
  private val code = thatName + " != " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs != value
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    lhs != value
  }
}

case class IsGlobalThatNotEqualToNumber(thatName: String, value: Number, termType: String ) extends RefBooleanExpression {
  private val code = thatName + " != " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isNotEqual(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isNotEqual(lhs, value, termType)
  }
}


/**
 * ISGREATER
 * @param thisName
 * @param thatName
 */
case class IsThisGreaterThanThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " > " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

case class IsThisGreaterThanEqualsThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " >= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

case class IsThatGreaterThanThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

case class IsThatGreaterThanEqualsThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

case class IsThatGreaterThanNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isGreaterThan(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isGreaterThan(lhs, value, termType)
  }
}

case class IsThatGreaterThanEqualsNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isGreaterThanOrEquals(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isGreaterThanOrEquals(lhs, value, termType)
  }
}

/**
 * ISGLOBALGREATER
 * @param thisName
 * @param thatName
 */
//** this.global > that.global
case class IsBothGlobalThisGreaterThanThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " > " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

//** this > that.global
case class IsThisGreaterThanGlobalThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " > " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

//** this.global > that
case class IsGlobalThisGreaterThanThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " > " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

//** that.global > this.global
case class IsBothGlobalThatGreaterThanThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

//** that > this.global
case class IsThatGreaterThanGlobalThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

//** that.global > this
case class IsGlobalThatGreaterThanThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, rhs, termType)
  }
}

//** this.global >= that.global
case class IsBothGlobalThisGreaterThanEqualsThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " >= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

//** this.global >= that
case class IsGlobalThisGreaterThanEqualsThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " >= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

//** this >= that.global
case class IsThisGreaterThanEqualsGlobalThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " >= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

//** that.global >= this.global
case class IsBothGlobalThatGreaterThanEqualsThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

//** that.global >= this
case class IsGlobalThatGreaterThanEqualsThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

//** that >= this.global
case class IsThatGreaterThanEqualsGlobalThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, rhs, termType)
  }
}

//** that.global > number
case class IsBothGlobalThatGreaterThanNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThan(lhs, value, termType)
  }
}

//** that.global >= number
case class IsBothGlobalThatGreaterThanEqualsNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isGreaterThanOrEquals(lhs, value, termType)
  }
}


/**
 * ISLESSTHAN
 * @param thisName
 * @param thatName
 * @param termType
 */
case class IsThisLessThanThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " < " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

case class IsThisLessThanEqualsThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " <= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

case class IsThatLessThanThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " > " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

case class IsThatLessThanEqualsThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " >= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
    //println("rhs: " + rhs)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

case class IsThatLessThanNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " < " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isLessThan(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isLessThan(lhs, value, termType)
  }
}

//** that <= number
case class IsThatLessThanEqualsNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " <= " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
//    println("lhs: " + lhs)
 //      println("rhs: " + value)
    ASTUtil.isLessThanOrEquals(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, value, termType)
  }
}

/**
 * ISGLOBALLESSTHAN
 *
 * @param thisName
 * @param thatName
 * @param termType
 */
//** this.global < that.global
case class IsBothGlobalThisLessThanThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " < " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

//** that.global < this.global
case class IsBothGlobalThatLessThanThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " < " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

//** that.global < this
case class IsGlobalThatLessThanThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " < " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

//** that < this.global
case class IsThatLessThanGlobalThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " < " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

//** this.global < that
case class IsGlobalThisLessThanThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " < " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

//** this < that.global
case class IsThisLessThanGlobalThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " < " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, rhs, termType)
  }
}

//** that.global <= this.global
case class IsBothGlobalThisLessThanEqualsThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " <= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

//** that.global <= this.global
case class IsBothGlobalThatLessThanEqualsThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " <= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

//** that <= this.global
case class IsThatLessThanEqualsGlobalThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " <= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

//** that.global <= this
case class IsGlobalThatLessThanEqualsThis(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thatName + " <= " + thisName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val rhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

//** this <= that.global
case class IsThisLessThanEqualsGlobalThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " <= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

//** this.global <= that
case class IsGlobalThisLessThanEqualsThat(thisName: String, thatName: String, termType: String) extends RefBooleanExpression {
  private val code = thisName + " <= " + thatName
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs =  ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent,thisName)
    val rhs = ASTUtil.getAnyValueForTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, rhs, termType)
  }
}

//** that.global < number
case class IsGlobalThatLessThanNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " < " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThan(lhs, value, termType)
  }
}

//** that.global <= number
case class IsGlobalThatLessThanEqualsNumber(thatName: String, value: Number, termType: String) extends RefBooleanExpression {
  private val code = thatName + " <= " + value
  override val node = new ReferenceASTNode(None, None, this, code)
  override def eval(thisEvent: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromSyntheticEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, value, termType)
  }
  override def evalBothCurated( thisEvent: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap): Boolean = {
    val lhs = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thatEvent, thatName)
    ASTUtil.isLessThanOrEquals(lhs, value, termType)
  }
}





