package com.experian.edf.oxygen.validation.xml

import java.io.{BufferedInputStream, InputStream}

import com.experian.edf.oxygen.domain.validation.FileValidationResult
import javax.xml.XMLConstants
import javax.xml.parsers.{SAXParser, SAXParserFactory}
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}

import scala.util.{Failure, Success, Try}

object XMLValidator {

  def validateFile(inputFile: String, xsdFile: String): Try[XsdValidationResult] = {
    val handler = new SaxParserHandler(inputFile)
    validateXml(readFileAsInputStream(inputFile, FileSystem.get(new Path(inputFile).toUri, new Configuration())), xsdFile, handler) match {
      case Success(result) =>
        if (result.parserException.isEmpty)
          Success(XsdValidationResult(success = true, result.startElement, List.empty[FileValidationResult]))
        else {
          val errors: List[FileValidationResult] = processErrors(handler)
          Success(XsdValidationResult(success = false, result.startElement, errors))
        }
      case Failure(exception) =>
        val errors: List[FileValidationResult] = processErrors(handler)
        if (errors.nonEmpty) {
          Success(XsdValidationResult(success = false, handler.startElement, errors))
        } else {
          Failure(
            new Exception(s"Validation Failure for $inputFile." +
              s" Exception ${exception.getLocalizedMessage}."))
        }
    }
  }

  private def processErrors(handler: SaxParserHandler): List[FileValidationResult] = {
    handler.parserException.toList
  }

  def validateXml(xmlStream: InputStream, xsdFile: String, handler: SaxParserHandler): Try[SaxParserHandler] = {
    Try({
      val saxParser = getSaxParser(readXsdSchema(xsdFile))
      saxParser.parse(xmlStream, handler)
      handler
    })
  }

  def readFileAsInputStream(from: String, fileSystem: FileSystem): BufferedInputStream = {
    new BufferedInputStream(fileSystem.open(new Path(from)))
  }

  def readXsdSchema(xsdFile: String): Schema = {
    val streamSource = new  StreamSource(getClass.getResourceAsStream(xsdFile))
    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(streamSource)
  }

  def getSaxParser(schema: Schema): SAXParser = {
    val saxFactory = SAXParserFactory.newInstance
    saxFactory.setNamespaceAware(true)
    saxFactory.setSchema(schema)
    saxFactory.newSAXParser()
  }
}

case class XsdValidationResult(success: Boolean, startElement: String, validationException: List[FileValidationResult])