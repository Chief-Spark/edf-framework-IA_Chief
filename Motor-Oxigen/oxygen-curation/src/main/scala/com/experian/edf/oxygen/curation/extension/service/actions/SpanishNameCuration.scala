package com.experian.edf.oxygen.curation.extension.service.actions

import com.experian.edf.oxygen.curation.extension.service.infotypes.SpanishName
import com.experian.edf.oxygen.curation.framework.actions.NameCuration

class SpanishNameCuration extends NameCuration {
  override def performAction(value: String): SpanishName = {
    nameParser(value)
  }

  private[this] def nameParser(fullName: String): SpanishName = {

    val parts = fullName.split("\\s+").toList

    val spanishName = if (parts.length == 1) new SpanishName("", "", parts(0), "")
    else if (parts.length == 2) new SpanishName(parts(0), "", parts(1), "")
    else if (parts.length == 3) new SpanishName(parts(0), "", parts(1), parts(2))
    else if (parts.length == 4) new SpanishName(parts(0), parts(1), parts(2), parts(3))
    else new SpanishName(parts(0), parts(1), parts(2), parts.drop(3).mkString(" "))

    spanishName
  }
}
