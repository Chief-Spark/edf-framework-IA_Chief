package com.experian.edf.oxygen.consistencycheck.engines.metric

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.OxygenService
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import org.slf4j.LoggerFactory.getLogger

object ConsistencyCheckMetricGenerator {

  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Curator which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): ConsistencyCheckMetricGenerator = {
    create(appContext)
  }

  def create[T <: OxygenAppContext](appContext: T): ConsistencyCheckMetricGenerator = {
    if (appContext.isInstanceOf[SparkOxygenAppContext])
      new SparkConsistencyMetricGenerator(appContext.asInstanceOf[SparkOxygenAppContext])
    else
      throw new OxygenException("Currently supported context is SparkOxygenAppContext")
  }

}

abstract class ConsistencyCheckMetricGenerator(appContext: OxygenAppContext) extends OxygenService(appContext) {
  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val metricsLogger = getLogger("metricsLogger")

  @throws(classOf[OxygenException])
  def run: Unit

}
