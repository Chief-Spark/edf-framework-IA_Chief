package com.experian.edf.oxygen.statecalculation.app


import com.experian.edf.oxygen.statecalculation.engines.initializer.SparkStateCalcInitializer
import com.experian.edf.oxygen.statecalculation.processors.StateCalculationContext
import com.experian.edf.oxygen.statecalculation.processors.defaults.DefaultFoundationStateEnrichmentProcessor

object StateCalcApp {

  def main(args: Array[String]) {

    val stateCalculator = SparkStateCalcInitializer.create(args)
    //val stateCalculator = SparkStateCalcInitializer.create(Array("local/env-local.conf", "local/statecalc-application-local.conf", "local/spark-local.conf"))

    val enrichmentContext: StateCalculationContext = StateCalculationContext(new DefaultFoundationStateEnrichmentProcessor, getConfiguration(), lookup = Map("key" -> "value"))
    stateCalculator.run(stateCalcContext = enrichmentContext)

  }
  private[this] def getConfiguration(): Map[String, AnyRef] = {
    val attConfig = Map("attConfig" -> "value")
    val otherConfig = Map("otherConfig" -> "value")
    val config = Map("attributesEngine" -> attConfig, "otherEngine" -> otherConfig)
    config
  }
}
