package com.experian.edf.oxygen.curation.engines.event

import com.experian.edf.oxygen.domain.internal.CuratedEvent


class DefaultOxygenCuratedEvent(curatedRec: String) extends CuratedEvent(curatedRec) {

  override def read(curatedRed: String): CuratedEvent = {
    new DefaultOxygenCuratedEvent(curatedRed)
  }

  override def write: String = {
    curatedRec
  }
}