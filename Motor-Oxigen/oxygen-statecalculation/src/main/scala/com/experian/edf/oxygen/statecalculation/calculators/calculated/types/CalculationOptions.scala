package com.experian.edf.oxygen.statecalculation.calculators.calculated.types

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-10 at 09:00
  */
case class CalculationOptions(
                               supportGrouping: Boolean = true,
                               supportWithoutGrouping: Boolean = true,
                               supportReturnAsValue: Boolean = true,
                               supportContains: Boolean = true,
                               preSelect: Boolean = true,
                               moreOptions: Map[String, Any] = Map.empty[String, Any]
                             ) extends Serializable
