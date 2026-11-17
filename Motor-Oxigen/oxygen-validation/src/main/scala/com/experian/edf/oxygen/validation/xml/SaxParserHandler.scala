package com.experian.edf.oxygen.validation.xml

import org.xml.sax.{Attributes, SAXParseException}
import org.xml.sax.helpers.DefaultHandler
import com.experian.edf.oxygen.domain.validation.FileValidationResult

import scala.collection.mutable.ListBuffer

class SaxParserHandler(val fileName: String) extends DefaultHandler with Serializable {

  var startElement: String = ""
  var parserException: ListBuffer[FileValidationResult] = ListBuffer.empty[FileValidationResult]

  override def startElement(uri: String, localName: String, qName: String, attributes: Attributes): Unit = {
    if (startElement.isEmpty) {
      if (localName != null && !localName.isEmpty) {
        startElement = localName
      } else {
        startElement = qName
      }
    }
  }

  override def warning(e: SAXParseException): Unit = {
    parserException.append(FileValidationResult(fileName, "warning", e.getLocalizedMessage, e.getLineNumber, e.getColumnNumber))
  }

  override def error(e: SAXParseException): Unit = {
    parserException.append(FileValidationResult(fileName, "error", e.getLocalizedMessage, e.getLineNumber, e.getColumnNumber))
  }

  override def fatalError(e: SAXParseException): Unit = {
    parserException.append(FileValidationResult(fileName, "fatal", e.getLocalizedMessage, e.getLineNumber, e.getColumnNumber))
  }

}
