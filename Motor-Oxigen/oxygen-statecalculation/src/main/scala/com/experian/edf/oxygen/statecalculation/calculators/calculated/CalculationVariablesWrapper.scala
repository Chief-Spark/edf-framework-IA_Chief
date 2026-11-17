package com.experian.edf.oxygen.statecalculation.calculators.calculated

import com.experian.edf.oxygen.statecalculation.GroupCache
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection


case class CalculationVariablesWrapper(
                                        section: CalculationSection,
                                        calculationStates: Map[String, Any],
                                        sectionByGroupCache: Map[String, Map[String, Any]],
                                        groupByMasterCache: GroupCache
                                      )