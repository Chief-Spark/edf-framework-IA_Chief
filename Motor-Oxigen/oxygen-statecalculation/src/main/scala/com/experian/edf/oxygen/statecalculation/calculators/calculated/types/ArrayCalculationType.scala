package com.experian.edf.oxygen.statecalculation.calculators.calculated.types

import com.experian.edf.oxygen.global.schemas.ElementType
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.statecalculation.calculators.calculated.CalculationFunctions.addContainment
import com.experian.edf.oxygen.statecalculation.calculators.calculated.{CalculationVariablesWrapper, ReturnAsProcessing}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.QueryFunctions.select
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList, GroupedRecordsMap}
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 12:28
  */
abstract class ArrayCalculationType(name: String, options: CalculationOptions) extends CalculationType(name.toUpperCase, options) {

  override final val elementType = ElementType.ARRAY

  def computeArrayType(section: CalculationSection, records: FoundationRecordList): FoundationRecordList

  def computeArrayTypeWithContainment(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: (String, FoundationRecordList)): (String, FoundationRecordList) = {
    val preSelectedRecords = if (options.preSelect) select(groupedRecords._2, OxygenCollectionsUtils.asScala(aggregationSectionVariables.section.select())) else groupedRecords._2
    if (options.supportContains) {
      val arrayType = computeArrayType(aggregationSectionVariables.section, preSelectedRecords)
      val records: List[FoundationRecord] = arrayType.map(each => addContainment(aggregationSectionVariables, each, groupedRecords._1))
      groupedRecords._1 -> records
    } else groupedRecords
  }

  def computeArrayTypeByGroup(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: GroupedRecordsMap): Map[String, FoundationRecordList] = groupedRecords.map(record => computeArrayTypeWithContainment(aggregationSectionVariables, record))

  override def compute(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: (String, FoundationRecordList)): (String, Any) = {
    val computedArrayType: (String, List[FoundationRecord]) = computeArrayTypeWithContainment(aggregationSectionVariables, groupedRecords)
    if (options.supportReturnAsValue)
      ReturnAsProcessing.processRecordList(aggregationSectionVariables, computedArrayType)
    else computedArrayType
  }

  override def computeByGroup(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: GroupedRecordsMap): Map[String, Any] = {
    val computedArrayTypeByGroup: Map[String, List[FoundationRecord]] = computeArrayTypeByGroup(aggregationSectionVariables, groupedRecords)
    if (options.supportReturnAsValue)
      ReturnAsProcessing.processRecordList(aggregationSectionVariables, computedArrayTypeByGroup)
    else computedArrayTypeByGroup
  }


}
