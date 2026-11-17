package com.experian.edf.oxygen.statecalculation.errors

import scala.collection.mutable.ListBuffer

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-01 at 08:30
  */
class Errors extends Serializable {

  private val errors = new ListBuffer[ErrorType]()

  def add(error: ErrorType): Unit = {
    errors += error
  }

  def addAll(error: List[ErrorType]): Unit = {
    errors ++= error
  }

  def isEmpty: Boolean = errors.isEmpty

  def hasErrors: Boolean = {
    errors.foreach(error => return error.severity == ErrorSeverity.ERROR)
    false
  }

  def getAll: List[ErrorType] = errors.toList

  def listMessages: List[String] = for (error <- getAll) yield error.toString

  def display: String = "\n" + listMessages.mkString("\n")
}
