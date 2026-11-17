package com.experian.edf.oxygen.validation.xml

import javax.xml.stream.{XMLEventReader, XMLInputFactory}
import java.io.BufferedInputStream
import java.util

import javax.xml.stream.events.{Attribute, EndElement, StartElement, XMLEvent}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class DefaultOxygenXmlEventReader extends OxygenXmlStreamReader {

  @transient private lazy val logger = getLogger(this.getClass)

  private lazy val xmlInputFactory = { XMLInputFactory.newInstance() }

  override def xmlEventReader(inputFile: String) = Try(xmlInputFactory.createXMLEventReader(readFileAsInputStream(inputFile, FileSystem.get(new Path(inputFile).toUri, new Configuration()))))

  def handleStartElement(xmlReader: XMLEventReader, startElement: StartElement): String = {
    val name = startElement.getName.getLocalPart
    val attributes: util.Iterator[_] = startElement.getAttributes
    val attrList: ListBuffer[String] = ListBuffer.empty[String]
    if (attributes.hasNext) {
      while (attributes.hasNext) {
        val attribute: Attribute = attributes.next().asInstanceOf[Attribute]
        val attrName = attribute.getName
        val attrVal = attribute.getValue
        attrList.append(s""""$attrName": "$attrVal"""")
      }
      return s"""{ "$name": { ${attrList.mkString(",")} } }"""
    }
    val nextEvent: XMLEvent = xmlReader.nextEvent
    s"""{"$name": "${nextEvent.asCharacters().getData}"}"""

  }

  def handleEndElement(xmlReader: XMLEventReader, endElement: EndElement): String = {
    ""
  }

  override def readTags(inputFile: String, tags: List[String]) : Array[String] = {
    if (tags.isEmpty) return Array.empty[String]

    val reader = xmlEventReader(inputFile) match {
      case Success(r) => r
      case Failure(exception) =>
        logger.error(s"Exception initializing XML event reader. ${exception.getLocalizedMessage} ")
        return Array.empty[String]
    }

    val results : ListBuffer[String] = ListBuffer.empty[String]
    val tagSet = mutable.Set(tags: _*)
    val check = Try(while (reader.hasNext && tagSet.nonEmpty) {
      val nextEvent = reader.nextEvent()
      if (nextEvent.isStartElement) {
        val startElement = nextEvent.asStartElement()
        if (tagSet.contains(startElement.getName.getLocalPart)) {
          results.append(handleStartElement(reader, startElement))
        }
      }
      if (nextEvent.isEndElement) {
        val endElement = nextEvent.asEndElement
        handleEndElement(reader, endElement)
        if (tagSet.contains(endElement.getName.getLocalPart)) {
          tagSet.remove(endElement.getName.getLocalPart)
        }
      }
    })
    check match {
      case Success(_) => logger.debug(s"Successfully read XML tags ${tags.mkString}")
      case Failure(exception) =>
        logger.error(s"Exception reading XML tags. ${exception.getLocalizedMessage} ")
    }
    results.toList.toArray
  }

  def readFileAsInputStream(from: String, fileSystem: FileSystem): BufferedInputStream = {
    new BufferedInputStream(fileSystem.open(new Path(from)))
  }
}
