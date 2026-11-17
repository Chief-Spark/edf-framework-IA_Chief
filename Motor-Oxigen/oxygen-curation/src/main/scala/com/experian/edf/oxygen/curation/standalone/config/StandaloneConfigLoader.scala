package com.experian.edf.oxygen.curation.standalone.config

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.configuration.configs.typesafe.TypesafeConfigLoader
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures

import java.io

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/28/20 at 12:25 AM
 */
object StandaloneConfigLoader {
  val configLoader = new TypesafeConfigLoader("template/template-config-default.conf")
  private lazy val logger = LoggerFactory.getLogger(this.getClass)


  def load(configLocations: List[io.File]): StandaloneConfig = {
    configLocations.foreach(each => {
      logger.debug(s"Loading Config File :$each")
      configLoader.load(each)
    })
    resolveFromConfig(configLoader.resolveConfig())
  }

  def load(configContent: String): StandaloneConfig = {
    configLoader.load(configContent)
    resolveFromConfig(configLoader.resolveConfig())
  }

  def load(configLocation: io.File): StandaloneConfig = load(List(configLocation))

  private def resolveFromConfig(conf: Config): StandaloneConfig = {
     import pureconfig.generic.auto._
    val loadedConfig = ConfigSource.fromConfig(conf).load[OxygenStandaloneConfiguration]
    loadedConfig match {
      case Left(a:ConfigReaderFailures) => a.toList.foreach(each => logger.error(each.description))
        throw new OxygenException("Errors found in config")
      case Right(b:OxygenStandaloneConfiguration) => b.oxygen.standaloneApp
    }

  }
}
