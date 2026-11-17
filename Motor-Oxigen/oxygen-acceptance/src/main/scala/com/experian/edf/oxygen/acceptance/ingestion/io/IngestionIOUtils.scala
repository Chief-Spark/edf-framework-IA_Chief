package com.experian.edf.oxygen.acceptance.ingestion.io

import com.experian.edf.global.exceptions.ConfigNotFoundException
import com.experian.edf.oxygen.acceptance.ingestion.IngestionMappers
import com.experian.edf.oxygen.acceptance.ingestion.config.{DatasetAcceptanceConfig, DatasetCurationConfig, DatasetValidationConfig}
import com.experian.edf.oxygen.utils.ScalaJacksonUtil

import scala.util.{Failure, Success, Try}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2/27/21 at 12:59 PM
 */
object IngestionIOUtils {
  def readIngestionMappers(location: String): Option[IngestionMappers] = read(location, classOf[IngestionMappers])

  def readDatasetValidationConfig(location: String): Option[DatasetValidationConfig] = read(location, classOf[DatasetValidationConfig])

  def readDatasetAcceptanceConfig(location: String): Option[DatasetAcceptanceConfig] = read(location, classOf[DatasetAcceptanceConfig])

  def readDatasetCurationConfig(location: String): Option[DatasetCurationConfig] = read(location, classOf[DatasetCurationConfig])

  private def read[T](location: String, cls: Class[T]): Option[T] = {
    val bufferedSourceAsString = Try(scala.io.Source.fromURL(getClass.getClassLoader.getResource(location))) match {
      case Success(bufferedSource) => bufferedSource.mkString.replace("\n", "")
      case Failure(exception) => throw new ConfigNotFoundException(s"Not able to load Config from Location: $location - ${exception.getMessage}")
    }
    ScalaJacksonUtil.fromJson(bufferedSourceAsString, cls)
  }
}
