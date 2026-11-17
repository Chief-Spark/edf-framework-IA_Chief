package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.impl

import java.math.BigDecimal
import com.experian.edf.oxygen.statecalculation.FoundationRecordList
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.{AggregateFunction, AggregateFunctionOptions}
import com.experian.edf.oxygen.statecalculation.calculators.utils.CalculationUtils.getValueFromMap


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-29 at 17:08
  */
class Count(name: String = "COUNT", options: AggregateFunctionOptions = AggregateFunctionOptions()) extends AggregateFunction[BigDecimal](name, options) {
  override def execute(foundationRecords: FoundationRecordList, fieldName: String): BigDecimal =
    new BigDecimal(foundationRecords.map(record => getValueFromMap(record, fieldName)).count(_ != null))

}
