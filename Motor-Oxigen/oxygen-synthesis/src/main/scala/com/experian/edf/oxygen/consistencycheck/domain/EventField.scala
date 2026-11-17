package com.experian.edf.oxygen.consistencycheck.domain

case class EUCEventField(name: String,
                         value: Any,
                         //referenceEventId: Long,
                         asOfDate: Long,
                         corrected:Boolean) extends  Serializable

case class LAPEventField(name: String,
                         value: Any,
                         //referenceEventId: Long,
                         asOfDate: Long) extends Serializable
