package com.experian.edf.oxygen.statecalculation.calculators.foundation

import com.experian.edf.oxygen.global.domain.statecalc.SyntheticEvent
import com.experian.edf.oxygen.statecalculation.{JavaFoundationRecord, JavaFoundationRecordList}
import com.experian.edf.oxygen.statecalculation.calculators.utils.CalculationUtils.toDataType
import com.experian.edf.oxygen.statecalculation.config.foundation.FoundationField
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationScriptExecutionException
import com.experian.edf.oxygen.statecalculation.processors.foundation.CalculatedWithHistoryFieldProcessor

import javax.script._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-04-16 at 17:49
  */
object FoundationFunctions {

  lazy val logger = LoggerFactory.getLogger(FoundationFunctions.getClass)

  def computeDerivedFoundationField(foundationField: FoundationField, syntheticEvent: SyntheticEvent)  = {

      val value = if (syntheticEvent.curatedRecordJson.containsKey(foundationField.name))
                      syntheticEvent.curatedRecordJson.get(foundationField.name)
                  else if (syntheticEvent.curatedRecordJson.containsKey(getNestedFieldName(foundationField.name)._1)) {
                      val (domain, elementName) = getNestedFieldName(foundationField.name)
                      syntheticEvent.curatedRecordJson.get(domain).asInstanceOf[Map[String, Any]].getOrElse(elementName, null)
                  }
                  else null

      Option(toDataType(foundationField.dataType, value))
  }


  def computeCalculatedWithHistoryField(foundationField: FoundationField, currentRecord: JavaFoundationRecord, previousRecords: JavaFoundationRecordList, creator: CalculatedWithHistoryFieldProcessor) = {
    val createdFieldValue = creator.createField(currentRecord, previousRecords)
    Option(toDataType(foundationField.dataType, createdFieldValue))
  }

  def computeCalculatedField(invokableEngine: Invocable, foundationField: FoundationField, foundationStateModel: JavaFoundationRecord): Option[Any] = {
    val value = try
      invokableEngine.invokeFunction("foundation_calculated_" + foundationField.name, foundationStateModel)
    catch {
      case e: Exception => throw new StateCalculationScriptExecutionException(e.getMessage + " - In Field : " + foundationField.name + " of foundation config")
    }
    Option(toDataType(foundationField.dataType(), value))
  }

  def constructCalculatedFieldJsFunction(engine: ScriptEngine, foundationField: FoundationField): Unit = {
    val value =
      s"""function foundation_calculated_${foundationField.name}(field){
         |  ${foundationField.value}
         |}""".stripMargin

    val compiledScript: CompiledScript = engine.asInstanceOf[Compilable].compile(value)
    compiledScript.eval()
    logger.debug(s"Registered Javascript Function to Engine for Foundation Field ${foundationField.name()}(CALCULATED):\n $value")
  }

  def getNestedFieldName(fieldName: String): (String, String) = {
    val str = fieldName.split("[.]")
    if(str.length == 2) (str(0), str(1)) else ("", "")
  }

  def getFieldName(field:FoundationField): String = {
    val str = field.name.split("[.]")
    if(str.length == 2) str(1) else field.name
  }
}
