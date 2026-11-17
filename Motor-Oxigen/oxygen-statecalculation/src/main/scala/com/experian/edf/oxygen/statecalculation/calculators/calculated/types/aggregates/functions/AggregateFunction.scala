package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions

import com.experian.edf.oxygen.statecalculation.FoundationRecordList
import com.experian.edf.oxygen.statecalculation.calculators.utils.CalculationUtils.getValueFromMap

import java.math.BigDecimal


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-29 at 16:47
  */
abstract class AggregateFunction[T](val name: String, val options: AggregateFunctionOptions) extends Serializable {
  def execute(foundationRecords: FoundationRecordList, fieldName: String): T

  protected def getField(foundationRecords: FoundationRecordList, fieldName: String): List[Any] = foundationRecords
    .map(record => getValueFromMap(record, fieldName))
    .filter(_ != null)

  protected def getFieldAsBigDecimal(foundationRecords: FoundationRecordList, fieldName: String): List[BigDecimal] = getField(foundationRecords, fieldName)
    .map(records => new BigDecimal(records.toString))

  protected def getFieldAsString(foundationRecords: FoundationRecordList, fieldName: String): List[String] = getField(foundationRecords, fieldName)
    .map(_.toString)

}
