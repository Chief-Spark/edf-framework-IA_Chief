package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.impl

import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.{CalculationOptions, ObjectCalculationType}
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList, builtInAggregateFunctions}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction
import com.experian.edf.oxygen.statecalculation.config.calculation.{AggregatesConfiguration, CalculationSection}
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationConfigException
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 12:47
  */
final class Aggregates(pluggenInAggregateFunctions: List[AggregateFunction[_]]) extends ObjectCalculationType("AGGREGATES", CalculationOptions(supportContains = false, supportReturnAsValue = false, preSelect = false)) {

  private lazy val logger: Logger = LoggerFactory.getLogger(classOf[Aggregates])


  val allAggregateFunctions: Map[String, AggregateFunction[_]] = (pluggenInAggregateFunctions ++ builtInAggregateFunctions)
    .map(funtion => funtion.name -> funtion).toMap

  allAggregateFunctions.foreach(types => logger.info(s"Aggregation Function Registered : ${types._1} -> ${types._2.getClass.getCanonicalName}"))


  override def computeObjectType(section: CalculationSection, groupedRecords: FoundationRecordList): FoundationRecord = {
    if (groupedRecords.nonEmpty) {
      val head = groupedRecords.head
      val selects: Map[String, Any] = if (section.select != null) section.select.asScala.map(select => select -> head(select)).toMap else Map.empty[String, Any]
      val map: Map[String, Any] = section.aggregates.asScala.map(agg => agg.name() -> computeAggregateFunction(section, agg, groupedRecords)).toMap
      map ++ selects
    } else Map.empty[String, Any]
  }

  def computeAggregateFunction(section: CalculationSection, aggregate: AggregatesConfiguration, foundationRecordList: FoundationRecordList): Any = {
    try {
      allAggregateFunctions(aggregate.function).execute(foundationRecordList, aggregate.field)
    } catch {
      case _: NoSuchElementException => throw new StateCalculationConfigException(s"In Calculation Section ${section.name}, Aggregation Function '${aggregate.function}' is not registered. " +
        s"Allowed values are ${allAggregateFunctions.keys.mkString(",")}. \n " +
        s"Refer https://bitbucketglobal.experian.local/projects/EDA/repos/oxygen-statecalculation-plugins/browse for pluggable aggregation Types")
    }
  }
}
