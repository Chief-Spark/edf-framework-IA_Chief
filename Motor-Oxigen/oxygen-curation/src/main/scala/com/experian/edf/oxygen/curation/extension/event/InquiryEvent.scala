package com.experian.edf.oxygen.curation.extension.event

case class InquiryEvent(timestamp: Long,
                   controlId: String,
                   dataSource: String,
                   isHardInquiry: Boolean,
                   payload: Map[String, AnyVal]) extends Serializable
