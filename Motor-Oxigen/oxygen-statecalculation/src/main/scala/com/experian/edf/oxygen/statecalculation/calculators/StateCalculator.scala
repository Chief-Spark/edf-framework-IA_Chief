package com.experian.edf.oxygen.statecalculation.calculators


import com.experian.edf.oxygen.statecalculation.utils.StateCalculatorContext

import javax.script.{Invocable, ScriptEngine, ScriptEngineManager}

/**
  * @author Abraham, Thomas - c15627a
  */
class StateCalculator(scContext: StateCalculatorContext, val engine: ScriptEngine = new ScriptEngineManager().getEngineByName("nashorn")) extends Serializable {
  val invokableEngine: Invocable = engine.asInstanceOf[Invocable]
  val guardRailFields: List[String] = List("dataSource", "subscriber", "subscriberRelation", "asOfPeriod").map(_.toUpperCase)
}
