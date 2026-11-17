package com.experian.edf.oxygen.curation.domain

/**
  * Created by c20286a on 7/16/17.
  */
@deprecated
private[oxygen] abstract class RawEvent(rawEvent: String) extends Serializable {

  def read(rawRecord: String): RawEvent
  def write: String

//  override def toString: String = rawEvent

}
