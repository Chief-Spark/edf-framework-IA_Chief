package com.experian.edf.oxygen.statecalculation.processors.defaults

import com.experian.edf.oxygen.statecalculation.processors.foundation.CalculatedWithHistoryFieldProcessor
import com.experian.edf.oxygen.statecalculation.{JavaFoundationRecord, JavaFoundationRecordList}

/**
  * @author Abraham, Thomas - c15627a
  */
class NullCalculatedWithHistoryFieldProcessor extends CalculatedWithHistoryFieldProcessor {
  //override def createField(currentEvent: Map[String, Any], pastEvents: List[Map[String, Any]]): Any = null
  override def createField(currentEvent: JavaFoundationRecord, pastEvents: JavaFoundationRecordList): Any = null
}