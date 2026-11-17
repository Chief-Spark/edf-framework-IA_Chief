package com.experian.edf.oxygen.consistencycheck.domain

case class DomainData(asOfDate: Option[Long], subscriberRelation: String, subscriber: Option[String] = None, datasource: Option[String] = None, fileName: Option[String] = None) extends Serializable
