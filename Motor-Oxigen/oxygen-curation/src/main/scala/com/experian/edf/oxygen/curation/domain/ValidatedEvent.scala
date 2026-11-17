package com.experian.edf.oxygen.curation.domain

import com.experian.edf.oxygen.domain.internal.{DomainData, FileMetadata}
import com.experian.edf.oxygen.validator.ObjectError

/**
  * Implementation specific method to determine the criteria for accepting / rejecting a record
  *
  * This method evaluates a record and returns:
  * (a) Quality Scores.  A quality score has a name and its associated integer value. Usually, higher the number better the quality.
  *     Quality scores is an array of scores expressed as a tuple or name and value.
  *     Name is the name of the quality metric and value is a score from 0 to 99. Higher the number better the quality score.
  *
  * (b) Error codes in the form of of tuples, where the first element is the error code and second element is an optional value.
  *
  * (c) A optional Boolean.  false value means reject the record.  true value means accept the record.
  *     A null value will let the framework decide the acceptance or rejection based on the configuration.
  *
  * All the rejected events are removed from further processing and is saved in a rejected file.
  *
  *
  * But it is up to the implementation to determine the metric name and its score.
  *
  * Note that an event can have multiple quality scores.
  *
  * The framework provides utility to aggregate the quality scores across each files and make it available
  * for further inspection.
  *
  * @return
  *      shouldAccept: Boolean
  */

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