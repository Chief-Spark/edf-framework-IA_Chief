package com.experian.edf.oxygen.statecalculation.processors.defaults

import com.experian.edf.oxygen.statecalculation.{JavaFoundationRecord, JavaFoundationRecordList}
import com.experian.edf.oxygen.statecalculation.processors.foundation.CalculatedWithHistoryFieldProcessor

/**
  * @author Abraham, Thomas - c15627a
  */
class DefaultCalculatedWithHistoryFieldProcessor extends CalculatedWithHistoryFieldProcessor {
  //override def createField(currentEvent: FoundationRecord, pastEvents: FoundationRecordList): Any = ???
  override def createField(currentEvent: JavaFoundationRecord, pastEvents: JavaFoundationRecordList): Any = ???
}
