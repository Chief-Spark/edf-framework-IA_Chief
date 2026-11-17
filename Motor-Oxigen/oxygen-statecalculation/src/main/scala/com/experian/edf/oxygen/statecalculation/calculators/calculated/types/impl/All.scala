package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.impl

import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.{ArrayCalculationType, CalculationOptions}
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 12:26
  */
class All extends ArrayCalculationType("ALL", CalculationOptions()) {

  override def computeArrayType(section: CalculationSection, groupedRecords: FoundationRecordList): List[FoundationRecord] =
    groupedRecords
}
