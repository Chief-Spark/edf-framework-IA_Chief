package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.utils.JsonUtils
import com.google.gson.JsonElement

abstract class SynthesisProcessor extends Serializable {

  def performFieldSynthesis(fieldName: String, subjectEventFieldValue: Option[JsonElement], existingSynthesizedValue: Option[JsonElement] ): JsonElement = ???

  def performFieldSynthesis(fieldName: String, subjectEventFieldValue: Option[Any], existingSynthesizedValue: Option[Any] ): Any = 
    {
      val value = performFieldSynthesis(fieldName,
        if (subjectEventFieldValue.isDefined) Some(mapToJsonObject(subjectEventFieldValue.get.asInstanceOf[ Map[String,Any]])) else None,
        if (existingSynthesizedValue.isDefined) Some(mapToJsonObject(existingSynthesizedValue.get.asInstanceOf[ Map[String,Any]])) else None
      )
      jsonObjectToMap(value)
      // TODO: map : figure out how to do this.. do it before merging
    }

  def mapToJsonObject(mapObject: Map[String,Any]): JsonElement = {
    JsonUtils.fromJson(JsonUtils.toJson[Map[String,Any]](mapObject),classOf[JsonElement])
  }

  def jsonObjectToMap(jsonObject: JsonElement): Map[String,Any] = {
    JsonUtils.fromJson(JsonUtils.toJson(jsonObject),classOf[Map[String,Any]])
  }

}
