package com.experian.edf.oxygen.curation.extension.service.infotypes

import com.experian.edf.oxygen.curation.framework.InformationType

case class FilingOffice(val mainCity: String = "", secondCity: String = "", number: Int = -1) extends InformationType {
  def this() = this("", "", -1)

  override def curationType: String = classOf[FilingOffice].getCanonicalName
}
