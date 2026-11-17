package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.domain.internal.Frequency

/**
  * Created by vinay on 7/20/17.
  *
  * Abstract class to hold file level information.
  */
abstract class FileMetadata(val fileName: String, headers: Array[String] = null) extends Serializable {

  @deprecated
  def headerRowCount: Int = 1

  /**
    * Source of the data/file
    */
  val dataSource: String

  /**
    * Must be in epoch time
    */
  val receivedDate: Long

  /**
    * Must be in epoch time
    */
  val fileAsOfDate: Long

  /**
    * Deprecating subscriber soon, subscriber can be populated through DomainData in "createValidatedEvent"
    *
    */
  //Do not delete subscriber field. Needed for BOS and DOT application : Thomas
  val subscriber: String

  val minFrequency: Frequency = Frequency.onceEveryMonth

  val fileTag: String = ""

  @deprecated
  val fileType: String = ""

  @deprecated
  val subSubscriber: String = ""

  @deprecated
  val additionalInformation: Map[String, String] = Map.empty

}
