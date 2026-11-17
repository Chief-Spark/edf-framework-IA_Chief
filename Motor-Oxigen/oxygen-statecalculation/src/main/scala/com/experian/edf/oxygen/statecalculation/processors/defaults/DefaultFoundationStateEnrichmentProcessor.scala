package com.experian.edf.oxygen.statecalculation.processors.defaults

import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList}
import com.experian.edf.oxygen.statecalculation.processors.foundation.FoundationStateEnrichmentProcessor


/**
  * @author Abraham, Thomas - c15627a
  */
class DefaultFoundationStateEnrichmentProcessor extends FoundationStateEnrichmentProcessor {
  override def erichFoundationState(foundationState: FoundationRecordList):  (FoundationRecordList,FoundationRecordList) = (foundationState,List.empty[FoundationRecord])
  //override def erichFoundationState(foundationState: JavaFoundationRecordList): JavaFoundationRecordList = foundationState
}
