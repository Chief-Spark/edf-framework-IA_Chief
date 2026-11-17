package com.experian.edf.oxygen.acceptance.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.OxygenOrchestratedService
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import org.slf4j.LoggerFactory._


/**
 * Companion object to the Acceptor class. Also serves as a factory class to instantiate different Acceptors.
 * The acceptor is the phase which comes in after validation.
 * The current phases are
 * Stage 1 -> Validation
 * Stage 2 -> Acceptance
 * Stage 3 -> Curation
 * Stage 4 -> Pinning
 *
 * @see SparkAcceptor which is the currently supported Acceptor
 */
object Acceptor {

  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The Acceptor which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): Acceptor = {
    create(appContext)
  }

  /**
   * The factory method to create an Acceptor
   *
   * @param appContext
   * @tparam T
   * @return
   */
  def create[T <: OxygenAppContext](appContext: T): Acceptor = {
    val config = appContext.config
    appContext match {
      case context: SparkOxygenAppContext => new SparkAcceptor(context)
        if (config.configMap("oxygen.application.lambda").equalsIgnoreCase("stream")) new SparkHighFrequencyAcceptor(appContext.asInstanceOf[SparkOxygenAppContext])
        else if (config.configMap("oxygen.application.lambda").equalsIgnoreCase("batch")) new SparkAcceptor(appContext.asInstanceOf[SparkOxygenAppContext])
        else throw new UnsupportedOperationException("only oxygen.application.lambda = stream / batch are supported!")
      case _ => throw new OxygenException("Currently supported context is SparkOxygenAppContext")
    }
  }
}

/**
 * Abstract acceptor class. Current instances are SparkAcceptor which implements acceptor using Apache Spark.
 * The current phases are
 * Stage 1 -> Validation
 * Stage 2 -> Acceptance
 * Stage 3 -> Curation
 * Stage 4 -> Pinning
 *
 * @param appContext The application context which is used by the acceptor.
 * @see SparkAcceptor
 */
private[oxygen] abstract class Acceptor(appContext: OxygenAppContext) extends OxygenOrchestratedService(appContext) {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val metricsLogger = getLogger("metricsLogger")
  @transient lazy val puLogger = getLogger("puLogger")


  /**
   * This is the method from the acceptors which is exposed to implementations. Create the Acceptor using an
   * appropriate context and then run the acceptor to start accepting files. @see SparkAcceptor which is the currently
   * implemented acceptor using Apache Spark
   *
   * @param incremental (Do we want to accept everything or only incrementally)
   * @throws com.experian.edf.oxygen.exception.OxygenException
   * @return (Any)
   */
  @throws(classOf[OxygenException])
  def run(incremental: Boolean = true): Any

}
