package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.utils.JsonUtils
import com.google.gson._

import java.lang.reflect.Type

object CustomRule {
  val actionTypeHide = "hide"
  val actionTypeSetFlag = "setFlag"
}

case class CustomRule(name: String,
                      check: String,
                      description: String,
                      precedence: Int,
                      action: String = CustomRule.actionTypeHide,
                      newFlagName: String = "custom",
                      duration: String,
                      fields: Array[String]) extends Serializable {

  def hideAllEvents: Boolean = {
    if (action == CustomRule.actionTypeHide && duration == null && fields.isEmpty) true
    else false
  }
}

object CustomRuleDeser extends JsonDeserializer[CustomRule] {
  override def deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CustomRule = {
    val action = if (json.getAsJsonObject.has("action")) json.getAsJsonObject.get("action").getAsString else CustomRule.actionTypeHide
    val newFlagName = if (json.getAsJsonObject.has("newFlagName")) json.getAsJsonObject.get("newFlagName").getAsString
    else null
    val duration = if (json.getAsJsonObject.has("duration")) json.getAsJsonObject.get("duration").getAsString else null
    val fields = if (json.getAsJsonObject.has("fields")) JsonUtils.fromJson(json.getAsJsonObject.get("fields").toString, classOf[Array[String]]) else Array.empty[String]
    CustomRule(json.getAsJsonObject.get("name").getAsString,
      json.getAsJsonObject.get("check").getAsString,
      json.getAsJsonObject.get("description").getAsString,
      json.getAsJsonObject.get("precedence").getAsInt,
      action,
      newFlagName,
      duration,
      fields)
  }
}
