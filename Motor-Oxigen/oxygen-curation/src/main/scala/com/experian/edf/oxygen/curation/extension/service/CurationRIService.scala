package com.experian.edf.oxygen.curation.extension.service

import com.experian.edf.oxygen.curation.extension.service.actions.{FilingOfficeCuration, SpanishNameCuration}
import com.experian.edf.oxygen.curation.extension.service.infotypes.{FilingOffice, SpanishName}
import com.experian.edf.oxygen.curation.framework.CurationService
import com.experian.edf.oxygen.curation.framework.infotype.Name

class CurationRIService extends CurationService {
  registerCurationAction(classOf[Name], classOf[SpanishNameCuration])
  registerCurationAction(classOf[FilingOffice], classOf[FilingOfficeCuration])

  override def curateName(value: String): SpanishName = curate(classOf[SpanishName], value).get()

  def curateFilingOffice(value: String): FilingOffice = curate(classOf[FilingOffice], value).get()

}
