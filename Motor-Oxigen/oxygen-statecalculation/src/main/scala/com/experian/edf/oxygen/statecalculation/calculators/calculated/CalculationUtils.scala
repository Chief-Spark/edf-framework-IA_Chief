package com.experian.edf.oxygen.statecalculation.calculators.calculated

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection

/**
  * @author Abraham, Thomas - c15627a
  */
private[calculated] object CalculationUtils extends Serializable {

  def getGroupIdentifier(section: CalculationSection): String = OxygenCollectionsUtils.asScala(section.groupBy).filter(!_.equals("pin")).mkString(",")

}
