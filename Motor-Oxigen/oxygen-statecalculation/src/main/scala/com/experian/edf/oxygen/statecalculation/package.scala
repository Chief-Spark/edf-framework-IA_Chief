package com.experian.edf.oxygen

import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.CalculationType
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.impl._
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.impl._

import java.util.{Map => JMap}


package object statecalculation {
  type FoundationRecord = Map[String, Any]
  type JavaFoundationRecord= JMap[String, Any]
  type FoundationRecordList = List[FoundationRecord]
  type JavaFoundationRecordList = List[JavaFoundationRecord]
  type GroupedRecordsList = List[FoundationRecordList]
  type GroupedRecordsMap = Map[String, FoundationRecordList]
  type GroupCache = Map[String, Map[String, FoundationRecordList]]

  val DEFAULT = "$$default##key$$"

  val CUSTOM_ON_SECTION = "CUSTOM_ON_SECTION"

  lazy val builtInAggregateFunctions: List[AggregateFunction[_]] = List(new Sum(), new Min(), new Max(), new Count(), new Average())

  lazy val builtInCalculationTypes: List[CalculationType] = List(new All(), new Distinct(), new MostRecentValue(), new MostFrequentValue())

}