package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}

/**
 * Reference Expression AST Traverser
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
object ReferenceGenerator {

  def postOrderTraverse(astNode: ReferenceASTNode, event: TaggedCuratedEventWithMap, thatevent : SyntheticEvent):Unit = {
    if (astNode == null)
      return
    postOrderTraverse(astNode.left.orNull, event, thatevent)
    postOrderTraverse(astNode.right.orNull, event, thatevent)
   astNode.value.eval(event, thatevent)
  }

  def traverseAST(astNode: ReferenceASTNode, event: TaggedCuratedEventWithMap, thatEvent: SyntheticEvent):Boolean = {
    val returnValue = astNode.value.eval(event, thatEvent)
    returnValue
  }

  def traverseASTForNonPeriodic(astNode: ReferenceASTNode, event: TaggedCuratedEventWithMap, thatEvent: TaggedCuratedEventWithMap):Boolean = {
    val returnValue = astNode.value.evalBothCurated(event, thatEvent)
    returnValue
  }

}
