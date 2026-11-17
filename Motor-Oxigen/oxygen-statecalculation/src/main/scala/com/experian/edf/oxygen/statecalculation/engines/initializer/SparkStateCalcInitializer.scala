package com.experian.edf.oxygen.statecalculation.engines.initializer

import com.experian.edf.oxygen.context.{AppContext, ApplicationContext}
import com.experian.edf.oxygen.domain.internal.TaggedCuratedEventWithMap
import com.experian.edf.oxygen.stage.statecalculation.{FoundationModel, XpmModel}
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationConfiguration
import com.experian.edf.oxygen.statecalculation.config.foundation.FoundationStateConfiguration
import com.experian.edf.oxygen.statecalculation.engines.StateCalculator

object SparkStateCalcInitializer {

  def create(configLocations: Array[String], myParent: AppContext = null): StateCalculator = {
    val appContext = ApplicationContext.create(configLocations, myParent, Array(classOf[FoundationStateConfiguration],
      classOf[CalculationConfiguration], classOf[TaggedCuratedEventWithMap], classOf[FoundationModel], classOf[XpmModel]))
    StateCalculator.create(appContext)
  }
// val appContext = ApplicationContext.create(Array("config/default/env.conf", "config/default/ht/statecalc-application.conf", "config/default/ht/oxygen-lookup.conf", "spark.conf"))
}
