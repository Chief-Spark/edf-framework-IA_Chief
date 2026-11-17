package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.StateCheck
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingField

import scala.collection.mutable

/**
 * Check Expression AST Traverser
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * Step 4: Traverse Check expression AST nodes and evaluate to derive a boolean
 */

case class CheckASTObject(val lookupService: OxygenLookup, val relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField]) {}

object CheckGenerator {

  /**
   * To evaluate periodic checks
   *
   * @param astNode
   * @param event
   * @param thatEvents
   * @param checkObject
   * @return
   */
  def traverseAST(astNode: CheckASTNode, event: TaggedCuratedEventWithMap, thatEvents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Boolean = {
    val returnValue = astNode.value.eval(event, thatEvents, checkObject, stateCheck)
    returnValue
  }

  /**
   * To evaluate non periodic checks
   *
   * @param astNode
   * @param event
   * @param thatEvents
   * @param checkObject
   * @return
   */
  def traverseASTForNonPeriodic(astNode: CheckASTNode, event: TaggedCuratedEventWithMap, thatEvents: List[TaggedCuratedEventWithMap], checkObject: CheckASTObject): Boolean = {
    val returnValue = astNode.value.evalForNonPeriodic(event, thatEvents, checkObject)
    returnValue
  }

  def postOrderTraverse(astNode: CheckASTNode, event: TaggedCuratedEventWithMap, thatevents: List[SyntheticEvent], checkObject: CheckASTObject, stateCheck: StateCheck): Unit = {
    if (astNode == null)
      return
    postOrderTraverse(astNode.left.orNull, event, thatevents, checkObject, stateCheck)
    postOrderTraverse(astNode.right.orNull, event, thatevents, checkObject, stateCheck)
    astNode.value.eval(event, thatevents, checkObject, stateCheck)
  }

}