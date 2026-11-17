package com.experian.edf.oxygen.statecalculation.schemas

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.statecalculation.builtInCalculationTypes
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.CalculationType
import com.experian.edf.oxygen.statecalculation.config.calculation.{CalculationConfiguration, CalculationSection}
import com.experian.edf.oxygen.statecalculation.config.foundation.{FoundationField, FoundationStateConfiguration}
import com.experian.edf.oxygen.statecalculation.utils.StateCalculationOptions


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-21 at 09:32
  */
class CalculationSchemaGenerator(stateCalculationConfiguration: StateCalculationOptions,
                                 foundationStateConfiguration: FoundationStateConfiguration,
                                 aggregationConfiguration: CalculationConfiguration) {

  private val calculationTypes: Map[String, CalculationType] = (builtInCalculationTypes ++ stateCalculationConfiguration.calculationTypes)
    .map(aggType => aggType.name.toUpperCase -> aggType).toMap

  private val foundationFields: Map[String, FoundationField] = OxygenCollectionsUtils.asScala(foundationStateConfiguration.fields())
    .flatMap(field => {
      val aliasFields: Map[String, FoundationField] = if (stateCalculationConfiguration.computeAliasInFoundation)
        OxygenCollectionsUtils.asScala(field.aliases()).map(a => a -> field).toMap
      else
        Map.empty[String, FoundationField]
      aliasFields ++ Map(field.name() -> field)
    }).toMap

  private val calculationSectionsEnabled: List[CalculationSection] = OxygenCollectionsUtils.asScala(aggregationConfiguration.calculations())
    .filter(_.enabled)

  private val calculationSections: Map[String, CalculationSection] = calculationSectionsEnabled
    .map(section => section.name() -> section).toMap


//  def generateSchema: SchemaElement = {
//    val elementsCache: mutable.Map[String, SchemaElement] = mutable.Map[String, SchemaElement]()
//
//    val elements = calculationSectionsEnabled.map(section => {
//      val element = getSchemaForSection(section, elementsCache.toMap)
//      elementsCache += (section.name -> element)
//      element
//    })
//    SchemaElement(null, ElementType.OBJECT, null, elements)
//  }
//
//  def getSchemaForSection(section: CalculationSection, elementsCache: Map[String, SchemaElement]): SchemaElement = if (section.`type`() == CUSTOM_ON_SECTION) ???
//  else calculationTypes(section.`type`).getSchema(AggregationSchemaVariablesWrapper(section, foundationFields, elementsCache, calculationSections))


}