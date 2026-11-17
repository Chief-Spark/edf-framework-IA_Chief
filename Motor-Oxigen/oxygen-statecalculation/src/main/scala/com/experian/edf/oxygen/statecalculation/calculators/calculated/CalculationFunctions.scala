package com.experian.edf.oxygen.statecalculation.calculators.calculated

import com.experian.edf.oxygen.statecalculation
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList}
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationReturn
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationExecutionException
import com.experian.edf.oxygen.statecalculation.processors.calculation.CustomOnSectionProcessor

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * @author Abraham, Thomas - c15627a
  */
private[calculated] object CalculationFunctions {

  def computeCustomOnSection(customOnSectionProcesserInstances: Map[String, CustomOnSectionProcessor], aggregationSectionVariables: CalculationVariablesWrapper, selects: List[String], lookup: Map[String, Any]): (String, Any) = {
    val selectedSections: Map[String, Any] = selects.map(select => select -> aggregationSectionVariables.calculationStates(select)).toMap
    val computedOutputFromSection: Map[String, Any] = customOnSectionProcesserInstances(aggregationSectionVariables.section.name).compute(aggregationSectionVariables.section, selectedSections, lookup)
    if (aggregationSectionVariables.section.returnAs() == CalculationReturn.VALUE && computedOutputFromSection != null && computedOutputFromSection.values != null)
      aggregationSectionVariables.section.name -> computedOutputFromSection.values.toList
    else
      aggregationSectionVariables.section.name -> computedOutputFromSection

  }


  def computingNumberOfPastPeriods(foundationRecords: FoundationRecordList, count: Integer): FoundationRecordList = {
    if (count != null && foundationRecords != null) foundationRecords.takeRight(count) else foundationRecords
  }

  def addContainment(aggregationSectionVariables: CalculationVariablesWrapper, foundationRecord: FoundationRecord, cacheIdentifierName: String): FoundationRecord = {
    if (aggregationSectionVariables.section.contains() != null && !aggregationSectionVariables.section.contains().isEmpty) {
      val containedFields: Map[String, Any] = aggregationSectionVariables.section.contains().asScala
        .map(contain => contain.name -> {
          try {
            val map: Map[String, Any] = aggregationSectionVariables.sectionByGroupCache(contain.section)
            if (map.nonEmpty)
              Try(map(cacheIdentifierName)).getOrElse(map(statecalculation.DEFAULT))
            else null
          } catch {
            case e: Exception => throw new StateCalculationExecutionException(s"Exception in contains : Section=${aggregationSectionVariables.section.name}, on Contains : '${contain.section}', cacheIdentifierName='$cacheIdentifierName'", e)
          }
        })
        .toMap
      foundationRecord ++ containedFields
    } else foundationRecord
  }
}
