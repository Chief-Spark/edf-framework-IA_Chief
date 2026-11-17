package com.experian.edf.oxygen.curation.domain

case class HFErrorEvent(error: String, inputTopic: String, value: String, stackTrace: String) extends Serializable
