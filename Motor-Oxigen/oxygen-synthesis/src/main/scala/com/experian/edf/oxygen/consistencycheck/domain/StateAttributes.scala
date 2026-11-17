package com.experian.edf.oxygen.consistencycheck.domain

case class StateAttributes(stateName: String, stateId: String) extends Serializable {
  val fieldsMap: Map[(String, String), String] = Map((stateName, stateId) -> stateId)
}

case class StateEventField(name: String,
                           stateIdentifier: String,
                           openDate: Option[Long],
                           closedDate: Option[Long]) extends Serializable

case class StateEvent( eventId: String,
                       stateName: String,
                       stateId: String,
                       isStatusOpen: Any,
                       asOfDate: Long,
                       subscriber: String,
                       subscriberRelation: String) extends Serializable