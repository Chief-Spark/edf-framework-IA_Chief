package com.experian.edf.oxygen.validation.domain


import com.experian.edf.oxygen.domain.internal.events.QualityScore
import com.experian.edf.oxygen.domain.RawEvent
import com.experian.edf.oxygen.domain.internal.{DomainData, FileMetadata}
import com.experian.edf.oxygen.validator.internal.ObjectError


abstract class ValidatedEvent(@transient val rawRecord: String) extends RawEvent(rawRecord) with Serializable {
  // val isValid: Boolean

  // Implementation specific method: Create myself from the raw record
  override def read(rawRecord: String): ValidatedEvent

  // Implementation specific method: Write myself out as a string
  override def write: String
}

class ValidatedEventValueObject(val event: ValidatedEvent, val domainData: Option[DomainData] = None,
                                val qualityScores: Array[QualityScore],
                                val errors: Array[ObjectError], val metadata: Option[FileMetadata] = None,
                                val headerErrors: Array[ObjectError] = Array.empty[ObjectError],
                                val footerErrors: Array[ObjectError] = Array.empty[ObjectError],
                                val oxygenCounters: Map[String, Int] = Map.empty[String, Int]
                               ) extends Serializable {


}
