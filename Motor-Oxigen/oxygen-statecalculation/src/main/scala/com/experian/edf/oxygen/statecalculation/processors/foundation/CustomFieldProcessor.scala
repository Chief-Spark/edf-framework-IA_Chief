package com.experian.edf.oxygen.statecalculation.processors.foundation

/**
  * @author Abraham, Thomas - c15627a
  */
abstract class CustomFieldProcessor extends Serializable {
  def createField(currentEvent: Map[String, Any]): Any
}
