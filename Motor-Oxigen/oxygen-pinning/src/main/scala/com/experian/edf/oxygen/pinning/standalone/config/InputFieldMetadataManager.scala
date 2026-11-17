package com.experian.edf.oxygen.pinning.standalone.config

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils.asScala
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import com.experian.edf.oxygen.pinning.stage.misc.configManager.{ConfigMetadataFile, MappingField}

import scala.io.BufferedSource

class InputFieldMetadataManager(val configFile: ConfigMetadataFile){

  lazy val identiFyKeys: List[String] = asScala(configFile.getIdentifyKeys)
  lazy val fieldValues: Map[String, MappingField] = asScala(configFile.getFields).map(record => (configFile.fieldRecordKey(record), record)).toMap

}


object InputFieldMetadataManager {


  def init(fileName: String): Option[InputFieldMetadataManager] = {
    val source: BufferedSource = scala.io.Source.fromURL(getClass.getResource(fileName))
    val fileData = source.mkString.replace("\n", "")
    val maybeStream: Option[ConfigMetadataFile] = ScalaJacksonUtil.fromJson(fileData, classOf[ConfigMetadataFile])
    if (maybeStream.isDefined) Option(new InputFieldMetadataManager(maybeStream.get)) else None
  }

}
