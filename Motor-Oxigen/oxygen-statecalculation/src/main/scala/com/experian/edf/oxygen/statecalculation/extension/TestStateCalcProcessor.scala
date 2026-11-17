package com.experian.edf.oxygen.statecalculation.extension

import com.experian.edf.oxygen.stage.statecalculation.processors.{StateCalcProcessor, StateCalculationContext}
import com.experian.edf.oxygen.utils.{JsonUtilExperimental, JsonUtils}
import org.slf4j.LoggerFactory.getLogger

/**
  * Extension Sample to Implement State Calculation Enrichment Engine
  * @param context StateCalculationContext: Any Configuration sent by the extension
  */
class TestStateCalcProcessor(context: StateCalculationContext) extends StateCalcProcessor(context) {

  private[this] val engine /*:AttributesEngine*/ = {
    val confStr = JsonUtilExperimental.toJson(context.enrichConf)
    val attributesEngine = "" //:AttributesEngine = new AttributesEngine(confStr)
    attributesEngine
  }

  @transient lazy val logger = getLogger(this.getClass)

  /***
    * Enrich a pin StateCalc output
    * @param payload
    * @return
    */
  override def enrich(payload: Map[String, Any]): Map[String, Any] = {

    val xpm = JsonUtils.toJson(payload)

    // The engine can be invoced and then results sent back to StateCalc to be appended
    //val results = engine.execute(xpm)

    val results = Map[String, Any] ("attr1" -> "value1", "attr2" -> "value2")

    results
  }
}