package com.experian.edf.oxygen.validation.extension

import com.experian.edf.oxygen.validation.domain.ValidatedEvent

class GenericJsonValidatedEvent(rec: String) extends ValidatedEvent(rec) {

  override def read(rawRecord: String): GenericJsonValidatedEvent = new GenericJsonValidatedEvent(rawRecord)

  override def write: String = rec
}

