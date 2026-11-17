package com.experian.edf.oxygen.statecalculation.processors.defaults

import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection
import com.experian.edf.oxygen.statecalculation.processors.calculation.CustomOnSectionProcessor


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-04-17 at 10:19
  */
class DefaultCustomOnSectionProcessor extends CustomOnSectionProcessor {
  override def compute(aggregationSection: CalculationSection, sections: Map[String, Any], lookup: Map[String, Any]): Map[String, Any] = Map("default" -> "value")
}
