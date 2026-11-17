package com.experian.edf.oxygen.statecalculation

import com.experian.edf.oxygen.global.domain.statecalc.SyntheticEvents
import com.experian.edf.oxygen.statecalculation.calculators.calculated.CalculationStateCalculator
import com.experian.edf.oxygen.statecalculation.calculators.foundation.FoundationStateCalculator
import com.experian.edf.oxygen.statecalculation.schemas.CalculationSchemaGenerator
import com.experian.edf.oxygen.statecalculation.utils.StateCalculatorContext

import javax.script.{ScriptEngine, ScriptEngineManager}


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-20 at 16:44
  */
class StateCalculator(context: StateCalculatorContext, engine: ScriptEngine = new ScriptEngineManager().getEngineByName("nashorn")) extends Serializable {

  private lazy val _schemaGenerator = new CalculationSchemaGenerator(context.stateCalculationConfiguration, context.foundationConfigurations, context.calculationConfigurations)

  lazy val foundationStateCalculator = new FoundationStateCalculator(context, this.engine)
  lazy val calculationStateCalculator = new CalculationStateCalculator(context, this.engine)


  def computeFoundationState(syntheticEvents: SyntheticEvents): (FoundationRecordList,FoundationRecordList)  =
    if (syntheticEvents.synthesizedEvents != null && !syntheticEvents.synthesizedEvents.isEmpty) foundationStateCalculator.computeFoundationState(syntheticEvents) else (List.empty[FoundationRecord],List.empty[FoundationRecord])

  def   computeCalculationState(foundationStates: List[Map[String, Any]]): Map[String, Any] = {
    if (foundationStates.nonEmpty)
      calculationStateCalculator.computeAggregatedState(foundationStates)
    else
      Map.empty[String, Any]
  }

  def computeState(syntheticEvents: SyntheticEvents): (Map[String, Any],List[Map[String, Any]]) = {
    val (foundationStates,additionalFoundationSates) = computeFoundationState(syntheticEvents)
    (computeCalculationState(foundationStates),additionalFoundationSates)
  }

  def schemaGenerator: CalculationSchemaGenerator = _schemaGenerator

}
