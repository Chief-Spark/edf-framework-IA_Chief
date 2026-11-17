package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.domain.vle.{VleMetadataFile, VleRecord, VleSizeClassifier}
import com.experian.edf.oxygen.domain.{TaggedCuratedEventWithMap, TaggedCuratedEvent}
import java.io.{File, InputStream}
import com.experian.edf.oxygen.domain.vle.VleSizeClassifier._
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils._
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

import scala.io.BufferedSource


/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/19/20 at 12:53 PM
 */
class VleMetadataManager(val vleFile: VleMetadataFile) extends Serializable {

  @transient  lazy val logger: Logger = getLogger(this.getClass)

  lazy val vleEnable: Boolean = vleFile.getEnable().booleanValue()
  lazy val vleKeys: List[String] = asScala(vleFile.getFilterKeysOrder)
  lazy val vleValues: Map[String, VleRecord] = asScala(vleFile.getRecords).map(record => (vleFile.celebrityRecordKey(record), record)).toMap

  def recordKey(tce: TaggedCuratedEventWithMap): String = {
    if (tce == null || tce.curatedRecordJson == null) { return "" }
    val recordKeyValues = vleKeys
      .map(tce.curatedRecordJson.get)
    if(recordKeyValues != null && recordKeyValues.nonEmpty) {
      recordKeyValues
        .map(x => if(x != null) x.toString else null)
        .mkString("_")
    } else ""
  }

  def thresholdValue(threshold: String): Long = {

    vleFile.thresholdValue(threshold).longValue()

  }

  def isVle(tce: TaggedCuratedEventWithMap): Boolean = vleValues contains recordKey(tce)

  def isVle(tce: TaggedCuratedEvent): Boolean = vleValues contains recordKey(TaggedCuratedEventWithMap.fromTaggedCuratedEvent(tce))

  def getVleRecord(tce: TaggedCuratedEventWithMap): Option[VleRecord] = vleValues get recordKey(tce)

  def getVleRecord(tce: TaggedCuratedEvent): Option[VleRecord] = vleValues get recordKey(TaggedCuratedEventWithMap.fromTaggedCuratedEvent(tce))

  def getVleRecord(key: String): Option[VleRecord] = vleValues get key

  def classify(fileSize: Long): VleSizeClassifier = {
    logger.debug("fileSize " + fileSize )
    val XLargeLimit = vleFile.getClassifierCriteria.getXLarge
    logger.debug("XLargeLimit " + XLargeLimit )
    val LargeLimit = vleFile.getClassifierCriteria.getLarge
    logger.debug("LargeLimit " + LargeLimit )
    val MediumLimit = vleFile.getClassifierCriteria.getMedium
    logger.debug("MediumLimit " + MediumLimit )

    val SmallLimit = vleFile.getClassifierCriteria.getSmall
    logger.debug("SmallLimit " + SmallLimit )
    val XSmallLimit = vleFile.getClassifierCriteria.getXSmall
    logger.debug("XSmallLimit " + XSmallLimit )

    val classification : VleSizeClassifier =  if (fileSize <= XSmallLimit) XSmall
    else if (fileSize > XSmallLimit && fileSize <= SmallLimit) Small
    else if (fileSize > SmallLimit && fileSize <= MediumLimit) Medium
    else if (fileSize > MediumLimit && fileSize <= LargeLimit) Large
    else if (fileSize > LargeLimit && fileSize <= XLargeLimit) XLarge
    else UnClassified
    logger.debug("Classification => " + classification )
    classification
  }
}

object VleMetadataManager extends Serializable {

  def init(file: File): Option[VleMetadataManager] = {
    val maybeFile = ScalaJacksonUtil.fromJson(file, classOf[VleMetadataFile])
    if (maybeFile.isDefined) Option(new VleMetadataManager(maybeFile.get)) else None
  }

  def init(stream: InputStream): Option[VleMetadataManager] = {
    val maybeStream: Option[VleMetadataFile] = ScalaJacksonUtil.fromJson(stream, classOf[VleMetadataFile])
    if (maybeStream.isDefined) Option(new VleMetadataManager(maybeStream.get)) else None
  }

  def init(vleFile: VleMetadataFile): VleMetadataManager = new VleMetadataManager(vleFile)

  def init(fileName: String): Option[VleMetadataManager] = {
    val source: BufferedSource = scala.io.Source.fromURL(getClass.getClassLoader.getResource(fileName))
    val fileData = source.mkString.replace("\n", "")
    val maybeStream: Option[VleMetadataFile] = ScalaJacksonUtil.fromJson(fileData, classOf[VleMetadataFile])
    if (maybeStream.isDefined) Option(new VleMetadataManager(maybeStream.get)) else None
  }
}
