package com.experian.edf.oxygen.statecalculation.utils

import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationConfiguration
import com.experian.edf.oxygen.statecalculation.config.foundation.FoundationStateConfiguration


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 12/12/19 at 9:52 AM
  *         State Calc Context from FW
  */
case class StateCalculatorContext(stateCalculationConfiguration: StateCalculationOptions,
                                  foundationConfigurationsAsList: List[FoundationStateConfiguration],
                                  calculationConfigurationsAsList: List[CalculationConfiguration],
                                  lookup: Map[String, Any] = Map.empty[String, Any]) extends Serializable {

  val foundationConfigurations: FoundationStateConfiguration = ContextUtils.mergeFoundationConfigs(foundationConfigurationsAsList)

  val calculationConfigurations: CalculationConfiguration = ContextUtils.mergeCalculationConfigs(calculationConfigurationsAsList)


}
