package com.experian.edf.oxygen.statecalculation.processors

import com.experian.edf.oxygen.domain.processors.OxygenProcessor


/**
  * StateCalc Processor is the abstract class the the extension needs to implement in order to
  * enrich the StateCalc output.
 *
  * @param context StateCalculationContext: Any Configuration sent by the extension
  */
abstract class StateCalcProcessor(context: StateCalculationContext) extends OxygenProcessor {

  /***
    * Enrich a state calculation aggregated output
    * @param payload
    * @return
    */
  def enrich(payload: Map[String, Any]): Map[String, Any]

}
