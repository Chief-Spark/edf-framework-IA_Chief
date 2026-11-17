package com.experian.edf.oxygen.statecalculation.calculators.calculated

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils._
import com.experian.edf.oxygen.statecalculation.calculators.StateCalculator
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction
import com.experian.edf.oxygen.statecalculation.calculators.calculated.CalculationFunctions.{computeCustomOnSection, computingNumberOfPastPeriods}
import com.experian.edf.oxygen.statecalculation.{CUSTOM_ON_SECTION, DEFAULT, FoundationRecord, FoundationRecordList, GroupedRecordsMap, builtInCalculationTypes}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.CalculationUtils.getGroupIdentifier
import com.experian.edf.oxygen.statecalculation.calculators.calculated.QueryFunctions.{groupBy, registerJSFunctionToEngine, where}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.CalculationType
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.impl.Aggregates
import com.experian.edf.oxygen.statecalculation.config.calculation.{CalculationSection, PluggableConfiguration}
import com.experian.edf.oxygen.statecalculation.errors.Errors
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationConfigException
import com.experian.edf.oxygen.statecalculation.processors.calculation.CustomOnSectionProcessor
import com.experian.edf.oxygen.statecalculation.utils.StateCalculatorContext
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters.asScalaBufferConverter
import javax.script.{ScriptEngine, ScriptEngineManager}
import scala.collection.mutable
import scala.util.Try

/**
  * @author Abraham, Thomas - c15627a
  */
class CalculationStateCalculatorVle(scContext: StateCalculatorContext, engine: ScriptEngine = new ScriptEngineManager().getEngineByName("nashorn")) extends StateCalculator(scContext, engine) {

  private lazy val logger: Logger = LoggerFactory.getLogger(classOf[CalculationStateCalculatorVle])

  private val aggregationFunctions: List[AggregateFunction[_]] = scContext.stateCalculationConfiguration.aggregateFunctions

  private val allCalculationTypes: List[CalculationType] = builtInCalculationTypes ++ Set(new Aggregates(aggregationFunctions)) ++ scContext.stateCalculationConfiguration.calculationTypes

  private val calculationTypes: Map[String, CalculationType] = allCalculationTypes
    .map(aggType => aggType.name.toUpperCase -> aggType).toMap

  calculationTypes.foreach(types => logger.info(s"Calculation Type Registered : ${types._1} -> ${types._2.getClass.getCanonicalName}"))


  //Filter disabled sections
  private val calculationSections: List[CalculationSection] = asScala(scContext.calculationConfigurations.calculations())
    .filter(_.enabled)
    .map(section => section.plugin(convertPluggableConfiguration(section)))

  //Constructing Javascript functions for where clause
  calculationSections
    .filter(aggregation => aggregation.where != null)
    .foreach(aggregationSection => registerJSFunctionToEngine(engine, aggregationSection))

  private val sectionToGroupIdentifierMap: Map[CalculationSection, String] = calculationSections
    .filter(section => section.groupBy != null && !section.groupBy.isEmpty)
    .map(section => section -> getGroupIdentifier(section)).toMap

  private val groupIdentifierToSectionMap: Map[String, List[CalculationSection]] = sectionToGroupIdentifierMap.groupBy(_._2).mapValues(_.keys.toList)
  private val groupIdentifierToGroupMap: Map[String, List[String]] = groupIdentifierToSectionMap.map(group => group._1 -> group._1.split(",").toList)
  private val nonTransientFields: List[String] = calculationSections.filter(!_.isTransient).map(_.name)

  private val customOnSectionProcesserInstances: Map[String, CustomOnSectionProcessor] = createCustomOnSectionInstances

  validateConfiguration

  def convertPluggableConfiguration(section: CalculationSection): PluggableConfiguration = {
    if (section.plugin != null) {
      val aggregationTypePluginClass: Class[_ <: PluggableConfiguration] = Try(calculationTypes(section.`type`()).configurationModel).getOrElse(classOf[PluggableConfiguration])
      val jsonNode = ScalaJacksonUtil.toJsonNode(section.plugin).get
      ScalaJacksonUtil.fromJsonNode(jsonNode, aggregationTypePluginClass).get
    } else section.plugin
  }

  def validateConfiguration: Errors = {
    val errors: Errors = new CalculationConfiguationValidator(scContext.calculationConfigurations).validate
    if (errors.hasErrors) {
      logger.error(errors.display)
      throw new StateCalculationConfigException(errors.display)
    } else if (!errors.isEmpty) logger.info(errors.display)
    errors
  }

  def computeAggregatedState(foundationState: FoundationRecordList): Map[String, Any] = {
    val startTime = System.currentTimeMillis()
    //Initial cache created by single GroupBy
    val groupByMasterCache: Map[String, Map[String, FoundationRecordList]] = groupIdentifierToGroupMap.map(group => group._1 -> groupBy(group._2, foundationState))
    //Map that saves the computed states
    val aggregationStates: mutable.Map[String, Any] = mutable.Map[String, Any]()
    //Map that caches Map[SectionName, Map[GroupValueCombination, GroupData]
    val aggregateSectionByGroupCache: mutable.Map[String, Map[String, Any]] = mutable.Map[String, Map[String, Any]]()

    calculationSections
      .foreach(aggregation => {
        val tuple = computeAggregationSection(CalculationVariablesWrapper(aggregation, aggregationStates.toMap, aggregateSectionByGroupCache.toMap, groupByMasterCache), foundationState)
        aggregateSectionByGroupCache += tuple._1
        aggregationStates += aggregation.name -> tuple._2
      })
    //Return after filtering Transient Sections
    val transientFilteredStates = aggregationStates.toMap.filter(state => nonTransientFields.contains(state._1))
    logger.debug("Aggregation state Time : " + (System.currentTimeMillis() - startTime) + " milliseconds")
    if (scContext.stateCalculationConfiguration.addParentEventsInAggregation) {
      val parentEventIds = computeParentEventList(foundationState)
////      val parentEventIdHash = Try(parentEventIds.map(ids => ids.toLong).sum).getOrElse(null)
//      val parentEventIdHash = transientFilteredStates.hashCode()
//      transientFilteredStates ++ Map("parentEvents" -> parentEventIds, "xpmHash" -> parentEventIdHash)
      transientFilteredStates ++ Map("parentEventIds" -> parentEventIds)
    }
    else transientFilteredStates
//    transientFilteredStates
  }

  def computeParentEventList(foundationState: FoundationRecordList): List[String] = {
    foundationState.flatMap(record => record("parentEventIds").asInstanceOf[java.util.List[String]].asScala.toList).distinct
  }

  private def computeAggregationSection(aggregationSectionVariables: CalculationVariablesWrapper, foundationState: FoundationRecordList): ((String, Map[String, Any]), Any) = {
    if (aggregationSectionVariables.section.groupBy != null && !aggregationSectionVariables.section.groupBy.isEmpty)
      withGrouping(aggregationSectionVariables, foundationState)
    else
      withoutGrouping(aggregationSectionVariables, foundationState)
  }

  private def withGrouping(aggregationSectionVariables: CalculationVariablesWrapper, foundationState: FoundationRecordList): ((String, Map[String, Any]), List[Any]) = {
    val groupedRecords: GroupedRecordsMap = aggregationSectionVariables.groupByMasterCache(sectionToGroupIdentifierMap(aggregationSectionVariables.section))
    val whereFilterWithPastPeriods: GroupedRecordsMap = groupedRecords
      .map(f => {
        val whereWithPeriodFilter = computingNumberOfPastPeriods(where(invokableEngine, aggregationSectionVariables.section, f._2), aggregationSectionVariables.section.numberOfPeriods)
        f._1 -> whereWithPeriodFilter
      })
    val whereFilterWithPastPeriodsOnlyNullValues: Map[String, FoundationRecordList] = whereFilterWithPastPeriods.filter(_._2 == null)

    val whereFilterWithPastPeriodsWithoutNullValues = whereFilterWithPastPeriods.filter(_._2 != null)

    val aggregationType: CalculationType = (Try(calculationTypes(aggregationSectionVariables.section.`type`.toUpperCase)) recoverWith {
      case _: NoSuchElementException => throw new UnsupportedOperationException(s"${aggregationSectionVariables.section.`type`} is not supported as an aggregation Type with Grouping clause : Supported Values: " + calculationTypes.keys.mkString(", ") + ". \n " +
        "Refer https://bitbucketglobal.experian.local/projects/EDA/repos/oxygen-statecalculation-plugins/browse for pluggable Calculation Types")
    }).get

    val fields: Map[String, Any] = aggregationType.computeByGroup(aggregationSectionVariables, whereFilterWithPastPeriodsWithoutNullValues).asInstanceOf[Map[String, Any]]

    val map = fields ++ whereFilterWithPastPeriodsOnlyNullValues
    (aggregationSectionVariables.section.name -> map, fields.values.toList)
  }


  private def withoutGrouping(aggregationSectionVariables: CalculationVariablesWrapper, foundationState: FoundationRecordList): ((String, Map[String, Any]), Any) = {
    val selects: List[String] = asScala(aggregationSectionVariables.section.select)
    val whereFilter: List[FoundationRecord] = where(invokableEngine, aggregationSectionVariables.section, foundationState)
    val withNumberOfPeriods = computingNumberOfPastPeriods(whereFilter, aggregationSectionVariables.section.numberOfPeriods)
    if (withNumberOfPeriods != null) {
      val whereFilterWithPastPeriods = DEFAULT -> withNumberOfPeriods
      val outputWithoutGrouping = if (aggregationSectionVariables.section.`type`.equals(CUSTOM_ON_SECTION)) {
        computeCustomOnSection(customOnSectionProcesserInstances, aggregationSectionVariables, selects, scContext.lookup)
      } else {
        val aggregationType: CalculationType = (Try(calculationTypes(aggregationSectionVariables.section.`type`.toUpperCase)) recoverWith {
          case _: Throwable => throw new UnsupportedOperationException(s"${aggregationSectionVariables.section.`type`} is not supported as an aggregation Type with Grouping clause : Supported Values: $CUSTOM_ON_SECTION, " + calculationTypes.keys.mkString(", "))
        }).get
        aggregationType.compute(aggregationSectionVariables, whereFilterWithPastPeriods).asInstanceOf[(String, Any)]
      }
      (aggregationSectionVariables.section.name -> Map(outputWithoutGrouping), outputWithoutGrouping._2)
    } else {
      (aggregationSectionVariables.section.name -> Map(DEFAULT -> null), null)
    }
  }

  private def createCustomOnSectionInstances: Map[String, CustomOnSectionProcessor] = {
    calculationSections.filter(_.`type`.equalsIgnoreCase(CUSTOM_ON_SECTION)).map(field => {
      if (field.handler() == null) throw new StateCalculationConfigException(s"Field ${field.name} is defined as $CUSTOM_ON_SECTION but no handler defined")
      val foundationFieldCreator = Class.forName(field.handler.clazz).newInstance().asInstanceOf[CustomOnSectionProcessor]
      field.name -> foundationFieldCreator
    }).toMap
  }

}
