package com.experian.edf.oxygen.statecalculation.utils

import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationConfiguration
import com.experian.edf.oxygen.statecalculation.config.foundation.FoundationStateConfiguration
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationConfigException


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 12/12/19 at 12:05 PM
  */
object ContextUtils extends Serializable {

  def mergeFoundationConfigs(foundationConfigurationsAsList: List[FoundationStateConfiguration]): FoundationStateConfiguration = {
    if (foundationConfigurationsAsList.nonEmpty) {
      val foundationConfig: FoundationStateConfiguration = new FoundationStateConfiguration(foundationConfigurationsAsList.head.domain())
      foundationConfigurationsAsList.foreach(f => foundationConfig.fields().addAll(f.fields()))
      foundationConfig
    } else {
      throw new StateCalculationConfigException("Foundation Configuration empty or null")
    }
  }

  def mergeCalculationConfigs(calculationConfigurationsAsList: List[CalculationConfiguration]): CalculationConfiguration = {
    if (calculationConfigurationsAsList.nonEmpty) {
      val aggregationConfig = new CalculationConfiguration(calculationConfigurationsAsList.head.domain())
      calculationConfigurationsAsList.foreach(f => if (f.calculations() != null) aggregationConfig.calculations().addAll(f.calculations()))
      calculationConfigurationsAsList.foreach(f => if (f.references() != null) aggregationConfig.references().addAll(f.references()))
      aggregationConfig
    }
    else {
      throw new StateCalculationConfigException("Calculation Configuration empty or null")
    }
  }

  def mergeListCalculationConfigs(calculationConfigurationsAsList: List[List[CalculationConfiguration]]): List[CalculationConfiguration] = {

    calculationConfigurationsAsList.map(elem => mergeCalculationConfigs(elem)).toList

  }

}
