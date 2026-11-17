package com.experian.edf.oxygen.consistencycheck.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.OxygenGenerationOrchestratedService
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import org.slf4j.LoggerFactory.getLogger


object ConsistencyChecker {
  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Curator which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): ConsistencyChecker = {
    create(appContext)
  }

  def create[T <: OxygenAppContext](appContext: T): ConsistencyChecker = {
    if (appContext.isInstanceOf[SparkOxygenAppContext])
      new SparkConsistencyChecker(appContext.asInstanceOf[SparkOxygenAppContext])
    else
      throw new OxygenException("Currently supported context is SparkOxygenAppContext")
  }

}

abstract class ConsistencyChecker(appContext: OxygenAppContext) extends OxygenGenerationOrchestratedService(appContext) {
  @transient lazy val logger = getLogger(this.getClass)

  @throws(classOf[OxygenException])
  def run: Unit

}