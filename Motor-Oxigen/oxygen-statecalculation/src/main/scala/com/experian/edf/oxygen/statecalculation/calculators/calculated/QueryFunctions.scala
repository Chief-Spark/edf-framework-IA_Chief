package com.experian.edf.oxygen.statecalculation.calculators.calculated

import java.util.{List => JavaList}
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils._
import com.experian.edf.oxygen.statecalculation.calculators.utils.CalculationUtils.getValueFromMap
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationScriptExecutionException
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList}

import javax.script.{Compilable, CompiledScript, Invocable, ScriptEngine}
import org.slf4j.LoggerFactory

/**
 * @author Abraham, Thomas - c15627a
 */
object QueryFunctions extends Serializable {

  lazy val logger = LoggerFactory.getLogger(QueryFunctions.getClass)

  //region Selectors

  def select(foundationFields: List[FoundationRecord], selection: List[String]): FoundationRecordList =
    foundationFields.map((fields: FoundationRecord) => select(fields, selection))

  def select(foundationFields: List[FoundationRecord], selection: JavaList[String]): FoundationRecordList =
    foundationFields.map((fields: FoundationRecord) => select(fields, asScala(selection)))

  def select(foundationFields: FoundationRecord, selection: List[String]): FoundationRecord =
    selection.map(select => select -> getValueFromMap(foundationFields, select)).toMap

  //endregion


  //region Group By

  def groupBy(groupBy: List[String], foundationState: FoundationRecordList): Map[String, FoundationRecordList] = {
    val listToRecords: Map[List[Any], FoundationRecordList] = foundationState
      .groupBy(field => groupBy.map(each => getValueFromMap(field, each)))
    listToRecords.map(record => record._1.mkString("") -> record._2)
  }

  //endregion


  //region Where Clause

  private val WHERE_CLAUSE_FUNCTION_PREFIX = "aggregated_where_"

  def where(invokableEngine: Invocable, aggregationSection: CalculationSection, foundationRecords: List[FoundationRecord]): List[FoundationRecord] =
    if (aggregationSection.where != null) {
      val records = foundationRecords
        .filter(record => {
          try {
            val whereClauseFunction = WHERE_CLAUSE_FUNCTION_PREFIX + aggregationSection.name
            invokableEngine.invokeFunction(whereClauseFunction, asJava(record)).toString.toBoolean
          }
          catch {
            case e: Exception => throw new StateCalculationScriptExecutionException(e.getMessage + " - Where condition in section : " + aggregationSection.name + " of aggregation config")
          }
        })
      if (records.isEmpty) null else records
    }
    else foundationRecords

  private[statecalculation] def registerJSFunctionToEngine(engine: ScriptEngine, aggregationSection: CalculationSection): Unit = {
    val whereClauseFunctionAsString: String = constructJSFunction(aggregationSection)
    val compiledScript: CompiledScript = engine.asInstanceOf[Compilable].compile(whereClauseFunctionAsString)
    compiledScript.eval()
    logger.debug(s"Registered Javascript Function to Engine for Calculation Section '${aggregationSection.name()}'(WHERE clause):\n $whereClauseFunctionAsString")
  }

  //endregion


  private def constructJSFunction(aggregationSection: CalculationSection): String = {
    val whereClauseFunctionName = WHERE_CLAUSE_FUNCTION_PREFIX + aggregationSection.name
    val jsFunctionString =
      s"""function $whereClauseFunctionName(field){
         |  ${aggregationSection.where}
         |}""".stripMargin
    jsFunctionString
  }
}
