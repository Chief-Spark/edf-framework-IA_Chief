package com.experian.edf.oxygen.validation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.{Constants, OxygenOrchestratedService}
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import org.slf4j.LoggerFactory.getLogger


/**
 * Companion object to the Discovery class. Also serves as a factory class to instantiate different Discovery instances.
 *
 * @see SparkDiscovery which is the currently supported Discovery
 */
object Discoverer {

  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Discovery which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): Discoverer = {
    create(appContext)
  }

  /**
   * The factory method to create an Discovery
   *
   * @param appContext
   * @tparam T
   * @return
   */
  def create[T <: OxygenAppContext](appContext: T): Discoverer = {
    appContext match {
      case context: SparkOxygenAppContext =>
        if (appContext.config.oxygen.isFeatureActive(Constants.features.ENABLE_MARKER_OPTIMISED_DISCOVERY) && appContext.config.oxygen.PROCESS_MANAGED_BY.toUpperCase() == Constants.OrchestratedBy.MARKERS)
          new SparkDiscovererWithMarkers(context)
        else new SparkDiscoverer(context)
      // new SparkDiscoverer(context)
      case _ => throw new OxygenException("Currently supported context is SparkOxygenAppContext")
    }
  }

}

/**
 * Abstract Discovery class.
 *
 * @param appContext The application context which is used by the discovery.
 * @see SparkDiscovery
 */
abstract class Discoverer(appContext: OxygenAppContext) extends OxygenOrchestratedService(appContext) {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val metricsLogger = getLogger("metricsLogger")

  @throws(classOf[OxygenException])
  def run(): List[String]

}