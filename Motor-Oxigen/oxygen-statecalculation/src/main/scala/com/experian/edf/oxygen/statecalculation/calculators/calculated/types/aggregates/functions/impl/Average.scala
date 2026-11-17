package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.impl

import com.experian.edf.oxygen.statecalculation.FoundationRecordList
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.{AggregateFunction, AggregateFunctionOptions}

import java.math.{BigDecimal, RoundingMode}
import scala.util.Try

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-29 at 17:08
  */
class Average(name: String = "AVERAGE", options: AggregateFunctionOptions = AggregateFunctionOptions()) extends AggregateFunction[BigDecimal](name, options) {
  val countFunction = new Count()
  val sumFunction = new Sum()

  override def execute(foundationRecords: FoundationRecordList, fieldName: String): BigDecimal = {
    val sum = sumFunction.execute(foundationRecords, fieldName)
    val count = countFunction.execute(foundationRecords, fieldName)
    Try(sum.divide(count, 4, RoundingMode.HALF_UP)).getOrElse(null)
  }

}
