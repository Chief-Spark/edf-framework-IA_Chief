package com.experian.edf.oxygen.curation.extension.service.actions

import com.experian.edf.oxygen.curation.extension.util.Util
import com.experian.edf.oxygen.curation.extension.service.infotypes.FilingOffice
import com.experian.edf.oxygen.curation.framework.CurationAction

class FilingOfficeCuration extends CurationAction[FilingOffice, String] {
  override def performAction(value: String): FilingOffice = {
    filingOfficeParser(value)
  }

  private[this] def filingOfficeParser(finingOffice: String): FilingOffice = {

    val parts = finingOffice.split("\\s+").toList

    val curatedFiningOffice = if (parts.length == 1) new FilingOffice(mainCity = parts(0))
    else if (parts.length == 2) new FilingOffice(mainCity = parts(0), secondCity = parts(1))
    else new FilingOffice(mainCity = parts(0), secondCity = parts(1), Util.toInt(parts(2)).getOrElse(-1))

    curatedFiningOffice
  }
}
