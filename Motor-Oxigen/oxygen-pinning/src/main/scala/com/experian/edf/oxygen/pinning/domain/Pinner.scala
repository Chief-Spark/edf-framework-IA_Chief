package com.experian.edf.oxygen.pinning.domain

import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.utils.ScalaJacksonUtil

case class PinnedTaggedIdentity(taggedIdentity: TaggedIdentity, pin: Long) extends Serializable

case class PinIndex(name: String, keys: List[String])
case class IndexPins(index: String, pins: List[String])

object SparkPinnerCommon {
  private[oxygen] def readPinIndex(pinIndexFile: String): Array[PinIndex] = {
    val indexStrings = readPinIndexFileAsString(pinIndexFile)
    val indexArray = indexStrings.split("\n").map(v => {
      ScalaJacksonUtil.fromJson[PinIndex](v).getOrElse(PinIndex("", List[String]()))
    })
    indexArray
  }

  private[oxygen] def readPinIndexFileAsString(filename: String): String = {
    scala.io.Source.fromURL(getClass.getClassLoader.getResource(filename)).mkString
  }
}

