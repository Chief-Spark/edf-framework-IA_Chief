package com.experian.edf.oxygen.statecalculation.utils


import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.CalculationType
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction
import com.experian.edf.oxygen.statecalculation.processors.defaults.DefaultFoundationStateEnrichmentProcessor
import com.experian.edf.oxygen.statecalculation.processors.foundation.FoundationStateEnrichmentProcessor

import scala.collection.mutable._

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-06-06 at 11:06
  */
case class StateCalculationOptions(
                                          foundationStateEnrichmentProcessor: FoundationStateEnrichmentProcessor = new DefaultFoundationStateEnrichmentProcessor(),
                                          computeAliasInFoundation: Boolean = false,
                                          addParentEventsInAggregation: Boolean = true,
                                          performFoundationEnrichment: Boolean = true
                                        ) extends Serializable {
  private val _calculationTypes: ListBuffer[CalculationType] = new ListBuffer[CalculationType]()
  private val _aggregateFunctions: ListBuffer[AggregateFunction[_]] = new ListBuffer[AggregateFunction[_]]()

  def calculationTypes: List[CalculationType] = _calculationTypes.toList

  def aggregateFunctions: List[AggregateFunction[_]] = _aggregateFunctions.toList

  def registerAggregationFunction(aggregateFunction: AggregateFunction[_]): StateCalculationOptions = {
    _aggregateFunctions += aggregateFunction
    this
  }

  def registerCalculationType(calculationType: CalculationType): StateCalculationOptions = {
    _calculationTypes += calculationType
    this
  }
}