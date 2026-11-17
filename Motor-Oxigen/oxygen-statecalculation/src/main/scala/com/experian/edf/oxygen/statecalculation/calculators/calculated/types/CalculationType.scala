package com.experian.edf.oxygen.statecalculation.calculators.calculated.types

import com.experian.edf.oxygen.global.schemas.ElementType
import com.experian.edf.oxygen.statecalculation.FoundationRecordList
import com.experian.edf.oxygen.statecalculation.calculators.calculated.CalculationVariablesWrapper
import com.experian.edf.oxygen.statecalculation.config.calculation.{CalculationSection, PluggableConfiguration}
import com.experian.edf.oxygen.statecalculation.errors.Errors


/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2019-05-09 at 12:28
 */
abstract class CalculationType(val name: String, options: CalculationOptions) extends Serializable {

  def compute(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: (String, FoundationRecordList)): Any

  def computeByGroup(aggregationSectionVariables: CalculationVariablesWrapper, groupedRecords: Map[String, FoundationRecordList]): Any

  def validate(aggregationSection: CalculationSection): Errors = new Errors()

  def configurationModel: Class[_ <: PluggableConfiguration] = classOf[PluggableConfiguration]

  final def getPluginConfiguration[A <: PluggableConfiguration : Manifest](aggregationSection: CalculationSection): A = {
    if (aggregationSection.plugin != null) aggregationSection.plugin.getClass.cast(aggregationSection.plugin).asInstanceOf[A] else aggregationSection.plugin.asInstanceOf[A]
  }

  val elementType: ElementType

  //  def getSchema(variables: AggregationSchemaVariablesWrapper): SchemaElement = {
  //    val select = asScala(variables.section.select())
  //    val selectionFields: List[SchemaField] = select
  //      .map(variables.foundationFields)
  //      .map(CalculationSchemaGeneratorUtil.fromFoundationField)
  //
  //    //For Return As VALUE
  //    val elementAggregationType = if (options.supportReturnAsValue && variables.section.returnAs() == CalculationReturn.VALUE) ElementType.VALUE else elementType
  //    val sectionName = if (elementType == ElementType.OBJECT || elementType == ElementType.ARRAY) variables.section.name() else null
  //
  //    //For Contains
  //    val contains = asScala(variables.section.contains())
  //    val containedElements = contains.map(contain => manageSchemaContainment(variables, contain))
  //    val sectionElement = SchemaElement(sectionName, elementAggregationType, selectionFields, containedElements)
  //
  //    //For Group By
  //    if (variables.section.groupBy() != null && !variables.section.groupBy().isEmpty)
  //      SchemaElement(variables.section.name(), ElementType.ARRAY, null, List(sectionElement))
  //    else sectionElement
  //
  //  }

  //  private def manageSchemaContainment(variables: AggregationSchemaVariablesWrapper, contains: Contains): SchemaElement = {
  //    val parentSection = variables.aggregationSections(contains.section)
  //    val currentSection = variables.section
  //    val cachedParentSchemaElement: SchemaElement = variables.elementsCache(parentSection.name)
  //    if (asScala(currentSection.groupBy).nonEmpty && asScala(parentSection.groupBy).nonEmpty) { // If both sections have groupByClause
  //      cachedParentSchemaElement.contains.head.copy(name = contains.name)
  //    } else if (asScala(currentSection.groupBy).nonEmpty && asScala(parentSection.groupBy).isEmpty) { // If only current section has groupBy
  //      cachedParentSchemaElement.copy(name = contains.name)
  //    } else if (asScala(currentSection.groupBy).isEmpty && asScala(parentSection.groupBy).nonEmpty) { // If only parent section has groupby and current section doesnt have. This is not supported
  //      throw new UnsupportedOperationException("You cannot add a contains on a Grouped Section to a non Grouped section. Vice versa is allowed")
  //    } else {
  //      throw new UnsupportedOperationException("Something is wrong in Containment.")
  //    }
  //  }

}
