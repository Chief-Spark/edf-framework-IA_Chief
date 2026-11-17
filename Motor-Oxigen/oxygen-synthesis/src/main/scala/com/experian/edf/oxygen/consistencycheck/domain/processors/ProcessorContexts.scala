package com.experian.edf.oxygen.consistencycheck.domain.processors

import com.experian.edf.oxygen.domain.validation.FileValidationResult
import com.experian.edf.oxygen.lookup.OxygenLookup

/**
 *
 * @author annapurna - C22603A
 *         Created on 3/18/20 at 3:56 PM
 */

case class LookupProcessorContext(lookup : OxygenLookup) extends Serializable

case class MigrationProcessorContext(lookup : OxygenLookup) extends Serializable

case class ConsistencyCheckProcessorContext(lookup : OxygenLookup) extends Serializable

case class CurationProcessorContext(lookup : OxygenLookup) extends Serializable

case class ProcessorContext(fileName: String, headers: Array[String]=Array.empty, footers: Array[String]=Array.empty, lookup: OxygenLookup, xsdResult: Array[FileValidationResult] = Array.empty[FileValidationResult]) extends Serializable

case class HFProcessorContext(lookup: OxygenLookup) extends Serializable

//case class AcceptorProcessorContext(lookup : Broadcast[OxygenLookup]) extends Serializable
case class AcceptorProcessorContext() extends Serializable
