package com.experian.edf.oxygen.consistencycheck.domain

case class Payload(data: Map[String, String]) extends Serializable

private[oxygen] object Payload extends Serializable {
  def empty: Payload = Payload(Map.empty[String, String])
}