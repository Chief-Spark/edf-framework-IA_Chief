package com.experian.edf.oxygen.statecalculation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.base.OxygenGenerationOrchestratedService
import com.experian.edf.oxygen.context.{OxygenAppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.domain.internal.TaggedCuratedEventWithMap
import com.experian.edf.oxygen.global.utils.ResourceUtils
import com.experian.edf.oxygen.utils.{JsonUtils, ScalaJacksonUtil}
import org.slf4j.LoggerFactory.getLogger
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils._
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationConfiguration
import com.experian.edf.oxygen.statecalculation.config.foundation.FoundationStateConfiguration
import com.experian.edf.oxygen.statecalculation.processors.StateCalculationContext

import scala.io.{BufferedSource, Source}


/**
 * We can have one processor stage read from multiple sources and output to multiple sinks
 *
 * @param appContext
 */
abstract class StateCalculator(appContext: OxygenAppContext) extends OxygenGenerationOrchestratedService(appContext) {
  @transient lazy val logger = getLogger(this.getClass)

  @throws(classOf[OxygenException])
  def run(config: Map[String, Any] = null, stateCalcContext: StateCalculationContext = StateCalculationContext()): Unit

}


object StateCalculator {
  @transient lazy val logger = getLogger(this.getClass)
  /**
   * Enable instantiation without the create method.
   *
   * @param appContext OxygenAppContext which contains the configuration
   * @tparam T A sub type of OxygenAppContext
   * @return The StateCalculator which has been created
   */
  def apply[T <: OxygenAppContext](appContext: T): StateCalculator = {
    create(appContext)
  }

  def create[T <: OxygenAppContext](appContext: T): StateCalculator = appContext match {
    case context: SparkOxygenAppContext => new SparkStateCalculator(context)
    case _ => throw new OxygenException("Currently supported context is SparkOxygenAppContext")
  }

  private[oxygen] def readFoundationConfiguration(file: String): FoundationStateConfiguration = {
    logger.info("*******************************************************************" + file)
    val dict = JsonUtils.fromJson(scala.io.Source.fromURL(
      getClass.getClassLoader.getResource(file))
      .mkString.replace("\n", ""), classOf[FoundationStateConfiguration])
   // val dict = ScalaJacksonUtil.fromJson(new BufferedSource(ResourceUtils.getResource(file))
   //   .mkString.replace("\n", ""), classOf[FoundationStateConfiguration]).get
    dict
  }

  private[oxygen] def readCalculationConfiguration(file: String): CalculationConfiguration = {
    val dict = ScalaJacksonUtil.fromJson(new BufferedSource(ResourceUtils.getResource(file))
      .mkString.replace("\n", ""), classOf[CalculationConfiguration]).get
    dict
  }

  private[oxygen] def readFoundationConfiguration(files: List[String]): List[FoundationStateConfiguration] = {
    files.map(fileName => {
      val file = Source.fromURL(getClass.getClassLoader.getResource(fileName))
      ScalaJacksonUtil.fromJson[FoundationStateConfiguration](file.mkString.replace("\n", "")).get
    })
  }

  private[oxygen] def readCalculationConfiguration(files: List[String]): List[CalculationConfiguration] = {
    files.map(fileName => {
      val file = Source.fromURL(getClass.getClassLoader.getResource(fileName))
      ScalaJacksonUtil.fromJson[CalculationConfiguration](file.mkString.replace("\n", "")).get
    })
  }

  private[oxygen] def saveStateEvents(pin: Long, stateEvents: String, stateEventStore: String, oxygenDataAccess: OxygenDataAccess) = {
    val keyValueEntry = new KeyValueEntry()
      .table(stateEventStore)
      .key(pin.toString)
      .data(stateEvents)

    oxygenDataAccess.setValue(keyValueEntry)
  }

  private[oxygen] def saveVleEvents(pin: Long, curatedEvent: TaggedCuratedEventWithMap, tableName: String, oxygenDataAccess: OxygenDataAccess) = {
    val previousData = oxygenDataAccess.getValue(new KeyValueEntry().table(tableName).key(pin.toString))
    val newData = if (previousData.isPresent) {
      ScalaJacksonUtil.fromJson(previousData.get(), classOf[List[TaggedCuratedEventWithMap]]) match {
        case Some(data) => data ++ List(curatedEvent)
        case None => List(curatedEvent)
      }
    } else List(curatedEvent)

    val keyValueEntry = new KeyValueEntry()
      .table(tableName)
      .key(pin.toString)
      .data(ScalaJacksonUtil.toJsonAsBytes(newData).get)

    oxygenDataAccess.setValue(keyValueEntry)
  }

}