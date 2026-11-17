package com.experian.edf.oxygen.statecalculation.calculators.calculated

import com.experian.edf.oxygen.statecalculation.config.calculation.{CalculationConfiguration, CalculationSection}
import com.experian.edf.oxygen.statecalculation.errors.{ErrorSeverity, ErrorType, Errors, StateCalculationConfigurationError}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-01 at 08:46
  */
class CalculationConfiguationValidator(calculationConfiguration: CalculationConfiguration) extends Serializable {

  lazy val logger = LoggerFactory.getLogger(classOf[CalculationConfiguationValidator])


  def validate: Errors = {
    val errors = new Errors()
    calculationConfiguration.calculations().asScala.foreach(aggregation => {
      validateByAggregationType(aggregation).foreach(errors.add)
    })
    errors
  }


  private def validateByAggregationType(section: CalculationSection): List[ErrorType] = {
    val errorTypes = section.`type`() match {
      case "AGGREGATES" => validateAggregateSection(section)
      case _ => List.empty[ErrorType]
    }
    errorTypes
  }

  private def validateAggregateSection(section: CalculationSection): List[ErrorType] = {
    val errors = new mutable.ListBuffer[StateCalculationConfigurationError]
    if (section.aggregates == null || section.aggregates.isEmpty)
      errors += StateCalculationConfigurationError("OXY-SC-1", s"Aggregation Section '${section.name}' of Type AGGREGATES does not have aggregates defined in configuration", ErrorSeverity.ERROR)
    else {
      if (section.groupBy == null || section.groupBy.isEmpty) {
        if (section.select() != null) errors += StateCalculationConfigurationError("OXY-SC-2", s"Selections are not allowed without GroupBy for Aggregation Type : AGGREGATES for section : '${section.name()}'", ErrorSeverity.ERROR)
      } else {
        if (section.select() != null) {
          val groupByElements: List[String] = section.groupBy.asScala.toList
          section.select().asScala.foreach(select => {
            if (!groupByElements.contains(select)) {
              errors += StateCalculationConfigurationError("OXY-SC-3", s"Field '$select' is not part of GroupBy clause in Aggregation Type : AGGREGATES for section : '${section.name}'", ErrorSeverity.ERROR)
            }
          })
        }
      }
    }
    errors.toList
  }

}
