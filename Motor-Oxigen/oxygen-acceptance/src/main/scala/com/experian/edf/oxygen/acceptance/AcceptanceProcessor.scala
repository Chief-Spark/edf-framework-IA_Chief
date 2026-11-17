package com.experian.edf.oxygen.acceptance

import com.experian.edf.oxygen.domain.acceptance.IAcceptanceProcessor

class AcceptanceProcessor extends Serializable with IAcceptanceProcessor {

  val historicalPeriodCount: Int = 0

  def attributeEvaluator(currentAttr: String, historicalAttr: Array[String]): Boolean = true
}