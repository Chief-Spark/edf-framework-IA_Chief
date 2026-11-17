package com.experian.edf.oxygen.pinning.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.OxygenGenerationOrchestratedService
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import org.slf4j.LoggerFactory.getLogger

/**
 * @author Abraham, Thomas - C15627A
 *         Created on 11/27/19 at 5:56 PM
 */
object HeterogeneousCollector {

  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Discovery which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): HeterogeneousCollector = create(appContext)

  /**
   * The factory method to create an Discovery
   *
   * @param appContext
   * @tparam T
   * @return
   */
  def create[T <: OxygenAppContext](appContext: T): HeterogeneousCollector = appContext match {
    case context: SparkOxygenAppContext => new SparkHeterogeneousCollector(context)
    case _ => throw new OxygenException("Currently supported context is SparkOxygenAppContext")
  }

}

/**
 * Abstract Discovery class.
 *
 * @param appContext The application context which is used by the discovery.
 * @see SparkDiscovery
 */
abstract class HeterogeneousCollector(appContext: OxygenAppContext) extends OxygenGenerationOrchestratedService(appContext) {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val metricsLogger = getLogger("metricsLogger")

  @throws(classOf[OxygenException])
  def run()

}