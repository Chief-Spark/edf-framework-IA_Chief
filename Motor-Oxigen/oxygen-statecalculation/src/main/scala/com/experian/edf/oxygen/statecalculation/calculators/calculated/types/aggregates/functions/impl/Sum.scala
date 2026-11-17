package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.impl

import com.experian.edf.oxygen.statecalculation.FoundationRecordList
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.{AggregateFunction, AggregateFunctionOptions}
import com.experian.edf.oxygen.statecalculation.calculators.utils.AggregatesUtils

import java.math.BigDecimal


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-29 at 17:08
  */
class Sum(name: String = "SUM", options: AggregateFunctionOptions = AggregateFunctionOptions()) extends AggregateFunction[BigDecimal](name, options) {
  override def execute(foundationRecords: FoundationRecordList, fieldName: String): BigDecimal = AggregatesUtils.sum(getFieldAsBigDecimal(foundationRecords, fieldName))

}
