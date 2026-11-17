package com.experian.edf.oxygen.validation.context

import com.experian.edf.oxygen.validation.domain.ApplicationRunMode
import com.google.gson.JsonArray

case class ValidationContext(applicationRunMode: ApplicationRunMode,
                             historicAttributes: Option[JsonArray] = None,
                             hfMetadata: Option[Map[String, Object]] = None) extends Serializable
