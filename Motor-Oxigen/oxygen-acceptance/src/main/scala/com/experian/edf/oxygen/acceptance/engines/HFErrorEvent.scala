package com.experian.edf.oxygen.acceptance.engines

case class HFErrorEvent(error: String, inputTopic: String, value: String, stackTrace: String) extends Serializable
