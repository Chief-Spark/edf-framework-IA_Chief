package com.experian.edf.oxygen.statecalculation.processors.calculation

import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-04-17 at 08:54
  */
abstract class CustomOnSectionProcessor {
  def compute(section: CalculationSection, sections: Map[String, Any], lookup: Map[String, Any] = Map.empty[String, Any]): Map[String, Any]
}
