package com.experian.edf.oxygen.acceptance.ingestion

import com.experian.edf.global.exceptions.ConfigNotFoundException
import com.experian.edf.oxygen.acceptance.ingestion.config.{DatasetAcceptanceConfig, DatasetCurationConfig, DatasetValidationConfig}
import com.experian.edf.oxygen.acceptance.ingestion.io.IngestionIOUtils

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2/25/21 at 8:12 PM
 */
case class IngestionConfigManager(ingestionMappers: IngestionMappers) extends Serializable {

  //TODO: Cache Config in advance instead of reading each time.

  private val patternToIngestionMapper: Map[String, IngestionMapper] = ingestionMappers.mappers.map(each => (each.pattern, each)).toMap

  def getDatasetValidationConfig(datasetPathToMatch: String): Option[DatasetValidationConfig] = {
    getIngestionMapper(datasetPathToMatch) match {
      case Some(mapper) => mapper.validation match {
        case Some(validationConfig) => IngestionIOUtils.readDatasetValidationConfig(validationConfig)
        case None => None
      }
      case None => None
    }
  }

  def getDatasetAcceptanceConfig(datasetPathToMatch: String): Option[DatasetAcceptanceConfig] = {
    getIngestionMapper(datasetPathToMatch) match {
      case Some(mapper) => mapper.acceptance match {
        case Some(acceptanceConfig) => IngestionIOUtils.readDatasetAcceptanceConfig(acceptanceConfig)
        case None => None
      }
      case None => None
    }
  }

  def getDatasetCurationConfig(datasetPathToMatch: String): Option[DatasetCurationConfig] = {
    getIngestionMapper(datasetPathToMatch) match {
      case Some(mapper) => mapper.curation match {
        case Some(curationConfig) => IngestionIOUtils.readDatasetCurationConfig(curationConfig)
        case None => None
      }
      case None => None
    }
  }

  def getIngestionMapper(datasetPath: String): Option[IngestionMapper] = {
    for (pattern <- patternToIngestionMapper) {
      if (datasetPath.matches(pattern._1)) {
        return Option(patternToIngestionMapper(pattern._1))
      }
    }
    None
  }

}

object IngestionConfigManager {
  def create(location: String): IngestionConfigManager = {
    val maybeMappers = IngestionIOUtils.readIngestionMappers(location)
    maybeMappers match {
      case Some(mappers) => IngestionConfigManager(mappers)
      case None => throw new ConfigNotFoundException(s"IngestionMapper configuration not found in location : $location")
    }
  }
}
