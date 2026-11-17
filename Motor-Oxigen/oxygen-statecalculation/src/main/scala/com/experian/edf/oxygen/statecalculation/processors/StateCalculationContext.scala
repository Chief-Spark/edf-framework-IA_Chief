package com.experian.edf.oxygen.statecalculation.processors

import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.CalculationType
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction
import com.experian.edf.oxygen.statecalculation.processors.defaults.DefaultFoundationStateEnrichmentProcessor
import com.experian.edf.oxygen.statecalculation.processors.foundation.FoundationStateEnrichmentProcessor


/**
  *
  * @author c59785a
  *         State Calc Context from Extension
  *
  *
  **/
case class StateCalculationContext(
                                    enrichmentProcessor: FoundationStateEnrichmentProcessor = new DefaultFoundationStateEnrichmentProcessor,
                                    enrichConf: Map[String, Any] = Map.empty[String, Any],
                                    calculationTypes: List[CalculationType] = List.empty[CalculationType],
                                    aggregationFunctions: List[AggregateFunction[_]] = List.empty[AggregateFunction[_]],
                                    lookup: Map[String, Any] = Map.empty[String, Any]
                                  ) extends Serializable
