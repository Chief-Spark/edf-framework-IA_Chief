package com.experian.edf.oxygen.curation.extension.event

import com.experian.edf.oxygen.domain.internal.CuratedEvent
import com.experian.edf.oxygen.utils.JsonUtils
import com.google.gson.JsonObject



class HFCuratedEvent(curatedRecord: String) extends CuratedEvent(curatedRecord) {

  @transient private val eventAsJson = JsonUtils.fromJson(curatedRecord, classOf[JsonObject])

  val idType = eventAsJson.get("idType")
  val nationalId = eventAsJson.get("nationalId")
  val firstName = eventAsJson.get("firstName")
  val lastName = eventAsJson.get("lastName")
  val accountNumber = eventAsJson.get("accountNumber")
  val endMostMessage = eventAsJson.get("endMostMessage")
  val newField: Option[String] = None

  override def read(curatedRecord: String): CuratedEvent = {
    new HFCuratedEvent(curatedRecord)
  }

  override def write: String = {
    JsonUtils.toJson(this)
  }

}