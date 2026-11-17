package com.experian.edf.oxygen.validation.xml

trait OxygenXmlEventReader extends Serializable {

  def readTags(inputFile: String, tags: List[String]): Array[String]

}
