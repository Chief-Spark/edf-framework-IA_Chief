package com.experian.edf.oxygen.statecalculation.engines

import com.experian.edf.oxygen.context.OxygenAppContext
import com.experian.edf.oxygen.global.domain.statecalc.SyntheticEvents
import com.experian.edf.oxygen.stage.statecalculation.{FoundationModel, XpmModel}
import com.experian.edf.oxygen.statecalculation.processors.StateCalculationContext
import org.apache.spark.rdd.RDD

abstract class AbstractSparkStateCalculator(appContext: OxygenAppContext) extends StateCalculator(appContext) {
  def compute(syntheticEvents: RDD[SyntheticEvents], config: Map[String, Any] = null, stateCalcContext: StateCalculationContext = StateCalculationContext()): (RDD[FoundationModel], RDD[XpmModel])

  def computeFoundation(syntheticEvents: RDD[SyntheticEvents], stateCalculationContext: StateCalculationContext): RDD[FoundationModel]

  def computeXpm(foundation: RDD[FoundationModel], stateCalculationContext: StateCalculationContext): RDD[XpmModel]
}