package com.experian.edf.oxygen.validation.xml

import javax.xml.stream.XMLEventReader

import scala.util.Try

abstract class OxygenXmlStreamReader extends OxygenXmlEventReader {

  def xmlEventReader(inputFile: String) : Try[XMLEventReader]

}
