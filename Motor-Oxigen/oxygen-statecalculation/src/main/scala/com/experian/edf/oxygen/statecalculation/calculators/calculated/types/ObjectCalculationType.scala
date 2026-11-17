package com.experian.edf.oxygen.statecalculation.calculators.calculated.types

import com.experian.edf.oxygen.global.schemas.ElementType
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.statecalculation.calculators.calculated.CalculationFunctions.addContainment
import com.experian.edf.oxygen.statecalculation.calculators.calculated.{CalculationVariablesWrapper, ReturnAsProcessing}
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList, GroupedRecordsMap}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.QueryFunctions.select
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection
/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 12:28
  */
abstract class ObjectCalculationType(name: String, options: CalculationOptions) extends CalculationType(name.toUpperCase, options) {

  override final val elementType = ElementType.OBJECT


  def computeObjectType(section: CalculationSection, records: FoundationRecordList): FoundationRecord

  def computeObjectTypeWithContainment(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: (String, FoundationRecordList)): (String, FoundationRecord) = {
    val preSelectedRecords = if (options.preSelect) select(groupedRecords._2, OxygenCollectionsUtils.asScala(aggregationSectionVariables.section.select())) else groupedRecords._2
    val objectType = computeObjectType(aggregationSectionVariables.section, preSelectedRecords)
    if (options.supportContains) {
      val arrayType: FoundationRecord = objectType
      val records = addContainment(aggregationSectionVariables, arrayType, groupedRecords._1)
      groupedRecords._1 -> records
    } else groupedRecords._1 -> objectType
  }

  def computeObjectTypeByGroup(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: GroupedRecordsMap): Map[String, FoundationRecord] = {
    groupedRecords.map(record => computeObjectTypeWithContainment(aggregationSectionVariables, record))
  }

  override def compute(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: (String, FoundationRecordList)): (String, Any) = {
    if (options.supportReturnAsValue)
      ReturnAsProcessing.processSingleRecord(aggregationSectionVariables, computeObjectTypeWithContainment(aggregationSectionVariables, groupedRecords))
    else computeObjectTypeWithContainment(aggregationSectionVariables, groupedRecords)
  }

  override def computeByGroup(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: GroupedRecordsMap): Map[String, Any] = {
    if (options.supportReturnAsValue)
      ReturnAsProcessing.processSingleRecord(aggregationSectionVariables, computeObjectTypeByGroup(aggregationSectionVariables, groupedRecords))
    else computeObjectTypeByGroup(aggregationSectionVariables, groupedRecords)
  }

}
