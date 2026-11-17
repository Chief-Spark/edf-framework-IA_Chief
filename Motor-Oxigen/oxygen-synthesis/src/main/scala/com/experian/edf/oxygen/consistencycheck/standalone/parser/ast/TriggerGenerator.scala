package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap

/**
 * Trigger Expression AST Generator
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
object TriggerGenerator {

  def postOrderTraverse(astNode: TriggerASTNode):Unit = {
    if (astNode == null)
      return
    postOrderTraverse(astNode.left.orNull)
    postOrderTraverse(astNode.right.orNull)

  }

  def postOrderTraverse(astNode: TriggerASTNode, event: TaggedCuratedEventWithMap):Unit = {
    if (astNode == null)
      return
    postOrderTraverse(astNode.left.orNull, event)
    postOrderTraverse(astNode.right.orNull, event)
   astNode.value.eval(event)
  }

  def traverseAST(astNode: TriggerASTNode, event: TaggedCuratedEventWithMap):Boolean = {
    val returnValue = astNode.value.eval(event)
    returnValue

  }

// do parsing, lexical
  // for each check get ast, do postorder traversal and evalute
}
