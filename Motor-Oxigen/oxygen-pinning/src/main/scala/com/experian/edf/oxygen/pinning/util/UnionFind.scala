package com.experian.edf.oxygen.pinning.util

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
 * Created by c15661a on 8/7/17.
 */
class UnionFind(val len: Int) {

  private case class Node(var parent: Int, var treeSize: Int)

  private val nodes = {
    val nodes = Array.fill[Node](len)(Node(-1, 1))
    var i = 0
    for (node <- nodes) {
      node.parent = i
      i = i + 1
    }
    nodes
  }

  /*
  Finds the root and also in that process compresses the path by updating the root node of children.
   */
  private def root(i: Int, prevNode: Node): Int = {
    if (nodes(i).parent == i) {
      i
    }
    else {
      if (prevNode != null)
        prevNode.parent = nodes(i).parent
      root(nodes(i).parent, nodes(i))
    }
  }

  private def findRoot(i: Int): Int = {
    if (nodes(i).parent == i) i
    else findRoot(nodes(i).parent)
  }

  def connected(p: Int, q: Int): Boolean = {
    p == q || findRoot(p) == findRoot(q)
  }

  def printNodes(): Unit = {
    for (i <- nodes.indices) {
      print(i + " -> (" + nodes(i).parent + ", " + nodes(i).treeSize + ", " + findRoot(i) + ") ")
    }
    println
  }

  def union(p: Int, q: Int): Unit = {
    val rootp = root(p, null)
    val rootq = root(q, null)

    if (rootp != rootq) {
      val nodeRootp = nodes(rootp)
      val nodeRootq = nodes(rootq)

      if (nodeRootp.treeSize < nodeRootq.treeSize) {
        nodeRootp.parent = rootq
      } else if (nodeRootp.treeSize > nodeRootq.treeSize) {
        nodeRootq.parent = rootp
      } else {
        nodeRootp.treeSize += 1
        nodeRootq.parent = rootp
      }
    }
  }

  def getAllUnions[T](items: Array[T]): List[List[T]] = {
    val hm = new mutable.HashMap[Int, ArrayBuffer[T]]
    for (i <- nodes.indices) {
      val root = findRoot(i)
      val tree = hm.getOrElse(root, null)

      if (tree == null) {
        val newTree = new ArrayBuffer[T]
        newTree += items(i)
        hm.put(root, newTree)
      }
      else
        tree += items(i)
    }
    val allUnions = new ArrayBuffer[List[T]]
    for ((key, value) <- hm) {
      allUnions += value.toList
    }
    allUnions.toList
  }
}