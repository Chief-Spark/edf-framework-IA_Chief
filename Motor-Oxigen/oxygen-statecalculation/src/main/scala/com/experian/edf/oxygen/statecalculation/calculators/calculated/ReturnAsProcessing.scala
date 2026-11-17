package com.experian.edf.oxygen.statecalculation.calculators.calculated

import com.experian.edf.oxygen.statecalculation.FoundationRecord
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationReturn


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 16:02
  */
object ReturnAsProcessing extends Serializable {
  def processRecordList(calculationCache: CalculationVariablesWrapper, data: Map[String, List[FoundationRecord]]): Map[String, Any] = {
    if (calculationCache.section.returnAs == CalculationReturn.VALUE) {
      val key = calculationCache.section.select().get(0)
      data.map(d => d._1 -> d._2.map(_ (key)))
    } else
      data
  }

  def processSingleRecord(calculationCache: CalculationVariablesWrapper, data: Map[String, FoundationRecord]): Map[String, Any] = {
    if (calculationCache.section.returnAs == CalculationReturn.VALUE) {
      val key = calculationCache.section.select().get(0)
      data.map(d => d._1 -> d._2(key))
    } else
      data
  }

  def processRecordList(calculationCache: CalculationVariablesWrapper, data: (String, List[FoundationRecord])): (String, Any) = {
    if (calculationCache.section.returnAs == CalculationReturn.VALUE) {
      val key = calculationCache.section.select().get(0)
      data._1 -> data._2.map(_ (key))
    } else
      data
  }

  def processSingleRecord(calculationCache: CalculationVariablesWrapper, data: (String, FoundationRecord)): (String, Any) = {
    if (calculationCache.section.returnAs == CalculationReturn.VALUE) {
      val key = calculationCache.section.select().get(0)
      data._1 -> data._2(key)
    }
    else
      data
  }

}
