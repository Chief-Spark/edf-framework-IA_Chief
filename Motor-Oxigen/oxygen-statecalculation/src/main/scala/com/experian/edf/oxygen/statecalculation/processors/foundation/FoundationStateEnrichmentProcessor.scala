package com.experian.edf.oxygen.statecalculation.processors.foundation

import com.experian.edf.oxygen.statecalculation.FoundationRecordList


/**
  * @author Abraham, Thomas - c15627a
  */
abstract class FoundationStateEnrichmentProcessor  extends  Serializable {
  def erichFoundationState(foundationState: FoundationRecordList): (FoundationRecordList,FoundationRecordList)
}
