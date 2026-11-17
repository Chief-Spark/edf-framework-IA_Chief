package com.experian.edf.oxygen.curation.extension.service.infotypes

import com.experian.edf.oxygen.curation.framework.infotype.Name

class SpanishName(val firstName: String, val lastName: String, val secondFirstName: String, val secondLastName: String) extends Name() {
  def this() = this("", "", "", "")
}
