package com.experian.edf.oxygen.statecalculation.processors.foundation

import com.experian.edf.oxygen.statecalculation.{JavaFoundationRecord, JavaFoundationRecordList}


/**
  * @author Abraham, Thomas - c15627a
  */
abstract class CalculatedWithHistoryFieldProcessor extends Serializable {
  def createField(currentEvent: JavaFoundationRecord, pastEvents: JavaFoundationRecordList): Any
}
