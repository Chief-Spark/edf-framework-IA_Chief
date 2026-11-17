package com.experian.edf.oxygen.consistencycheck.extension

import com.experian.edf.oxygen.consistencycheck.domain.SynthesisProcessor
import com.google.gson.{JsonArray, JsonElement}

class SyntheticFieldCustomProcessor extends SynthesisProcessor {

  override def performFieldSynthesis(fieldName: String, subjectEventFieldValue: Option[JsonElement], existingSynthesizedValue: Option[JsonElement]): JsonElement = {
    if (fieldName == "accountInformation") {
      val subjArray = if (subjectEventFieldValue.isDefined) subjectEventFieldValue.get.getAsJsonArray else new JsonArray()
      val synthesizedArray = if (existingSynthesizedValue.isDefined) existingSynthesizedValue.get.getAsJsonArray else new JsonArray()
      if (subjArray.size()>0)
      for(i <- 0 until subjArray.size()){
        synthesizedArray.add(subjArray.get(i))
      }
      if (synthesizedArray.size()>0) synthesizedArray else null
    }
    else if (fieldName == "accountInformationEUC") {
      if (subjectEventFieldValue.isDefined && subjectEventFieldValue.get != null)
      if (existingSynthesizedValue.isDefined) {
        val subjectValue = subjectEventFieldValue.get.getAsJsonObject
        val synthesizedValue = existingSynthesizedValue.get.getAsJsonArray
         synthesizedValue.add(subjectValue)
        synthesizedValue
      } else null
      else
        subjectEventFieldValue.orNull
    }
    else if (fieldName == "accountInformationLAP") {
      if (subjectEventFieldValue.isDefined) subjectEventFieldValue.orNull else existingSynthesizedValue.orNull
    }
    else
    if (subjectEventFieldValue.isDefined) subjectEventFieldValue.get else null
  }
}
