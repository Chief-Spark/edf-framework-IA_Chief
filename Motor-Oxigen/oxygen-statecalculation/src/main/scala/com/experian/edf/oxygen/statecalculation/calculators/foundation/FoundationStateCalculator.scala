package com.experian.edf.oxygen.statecalculation.calculators.foundation

import java.util.{Map => JMap}
import com.experian.edf.oxygen.global.domain.statecalc.{SyntheticEvent, SyntheticEvents}
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils._
import com.experian.edf.oxygen.statecalculation.{FoundationRecordList, JavaFoundationRecord}
import com.experian.edf.oxygen.statecalculation.calculators.StateCalculator
import com.experian.edf.oxygen.statecalculation.calculators.foundation.FoundationFunctions.{computeCalculatedField, computeCalculatedWithHistoryField, computeDerivedFoundationField, constructCalculatedFieldJsFunction, getFieldName}
import com.experian.edf.oxygen.statecalculation.calculators.utils.CalculationUtils
import com.experian.edf.oxygen.statecalculation.calculators.utils.CalculationUtils.toDataType
import com.experian.edf.oxygen.statecalculation.config.foundation.{FieldType, FoundationField}
import com.experian.edf.oxygen.statecalculation.exceptions.StateCalculationConfigException
import com.experian.edf.oxygen.statecalculation.processors.foundation.{CalculatedWithHistoryFieldProcessor, CustomFieldProcessor}
import com.experian.edf.oxygen.statecalculation.utils.StateCalculatorContext

import javax.script.{ScriptEngine, ScriptEngineManager}
import org.slf4j.LoggerFactory

import scala.collection.convert.ImplicitConversions.{`collection AsScalaIterable`, `map AsScala`}
import scala.collection.mutable.ListBuffer

/**
  * @author Abraham, Thomas - c15627a
  */
class FoundationStateCalculator(scContext: StateCalculatorContext, engine: ScriptEngine = new ScriptEngineManager().getEngineByName("nashorn")) extends StateCalculator(scContext, engine) {
  lazy val logger = LoggerFactory.getLogger(classOf[FoundationStateCalculator])
  val foundationFields: List[FoundationField] = asScala(scContext.foundationConfigurations.fields).filter(f => !guardRailFields.contains(f.name.toUpperCase))

  foundationFields.filter(_.`type`() == FieldType.CALCULATED).foreach(field => constructCalculatedFieldJsFunction(engine, field))
  private val customWithHistoryFieldInstances: Map[String, CalculatedWithHistoryFieldProcessor] = createCustomWithHistoryInstances
  private val customFieldInstances: Map[String, CustomFieldProcessor] = createCustomFieldInstances

  def computeFoundationState(syntheticEvents: SyntheticEvents): (FoundationRecordList,FoundationRecordList) = {
    val startTime = System.currentTimeMillis()
    val foundationStates = new ListBuffer[JavaFoundationRecord]
    val sortedSyntheticEvents: List[SyntheticEvent] = OxygenCollectionsUtils.asScala(syntheticEvents.synthesizedEvents)
      .filter(event => {
        val isVisible = CalculationUtils.getValueFromMap(asScala(event.curatedRecordJson()), "isVisible")
        !(isVisible != null && isVisible.isInstanceOf[Boolean] && !isVisible.asInstanceOf[Boolean])
      })
      .sortBy(_.asOfPeriod().epochDateTime())
    sortedSyntheticEvents.foreach(syntheticEvent => {
      val foundationRecord = new java.util.HashMap[String, Any]()

      foundationRecord.put("pin", syntheticEvents.pin)
      foundationRecord.put("dataSource", syntheticEvent.dataSource)
      foundationRecord.put("subscriber", syntheticEvent.subscriber)
      foundationRecord.put("subscriberRelation", syntheticEvent.subscriberRelation)
      foundationRecord.put("asOfPeriod", syntheticEvent.asOfPeriod().epochDateTime)
      foundationRecord.put("parentEventIds", syntheticEvent.parentEventIds.toList)
      foundationFields.filter(_.`type`() == FieldType.DERIVED)
        .foreach(field => {
          val value = computeDerivedFoundationField(field, syntheticEvent).getOrElse(toDataType(field.dataType(), field.defaultValue))
          if (value != null) {
            foundationRecord.put(getFieldName(field), value)
            addAliases(foundationRecord, field, value)
          }
        })
      foundationFields.filter(_.`type`() != FieldType.DERIVED)
        .foreach(field => {
          field.`type`() match {
            case FieldType.CALCULATED => computeCalculatedFields(foundationRecord, field)
            case FieldType.CUSTOM_WITH_HISTORY => computeCustomWithHistoryField(foundationStates, foundationRecord, field)
            case FieldType.CUSTOM => computeCustomField(foundationRecord, field)
            case FieldType.UNKNOWN => throw new StateCalculationConfigException(s"UNKNOWN is not a supported field Type for ${field.name()}. Please remove the same from the configuration. \n" +
              s"Allowed types are ${FieldType.values().toList.filter(!_.equals(FieldType.UNKNOWN)).mkString(", ")}")
          }
        })

      // TODO: CUSTOM
      foundationStates += foundationRecord
    })
    logger.debug(s"Foundation Time for ${syntheticEvents.pin}: " + (System.currentTimeMillis() - startTime) + " milliseconds")
    scContext.stateCalculationConfiguration.foundationStateEnrichmentProcessor.erichFoundationState(foundationStates.map(_.toMap).toList)
  }
  private def computeCustomWithHistoryField(foundationStates: ListBuffer[JavaFoundationRecord], foundationRecord: JavaFoundationRecord, field: FoundationField) = {
    val creator: CalculatedWithHistoryFieldProcessor = customWithHistoryFieldInstances(field.name)
    val value = computeCalculatedWithHistoryField(field, foundationRecord, foundationStates.toList, creator).getOrElse(toDataType(field.dataType(), field.defaultValue))
    if (value != null) {
      foundationRecord.put(field.name, value)
      addAliases(foundationRecord, field, value)
    }
  }

  private def computeCalculatedFields(foundationRecord: JMap[String, Any], field: FoundationField): Unit = {
    val value = computeCalculatedField(invokableEngine, field, foundationRecord).getOrElse(toDataType(field.dataType(), field.defaultValue))
    if (value != null) {
      foundationRecord.put(field.name, value)
      addAliases(foundationRecord, field, value)
    }
  }

  private def computeCustomField(foundationRecord: JMap[String, Any], field: FoundationField): Unit = {
    val createdFieldValue = customFieldInstances(field.name).createField(foundationRecord.toMap)
    val value = Option(toDataType(field.dataType, createdFieldValue)).getOrElse(toDataType(field.dataType(), field.defaultValue))
    if (value != null) {
      foundationRecord.put(field.name, value)
      addAliases(foundationRecord, field, value)
    }
  }

  private def addAliases(foundationRecord: JMap[String, Any], field: FoundationField, value: Any): Unit = {
    if (value != null && scContext.stateCalculationConfiguration.computeAliasInFoundation && field.aliases() != null && !field.aliases().isEmpty) field.aliases().foreach(alias => foundationRecord.put(alias, value))
  }

  private def createCustomWithHistoryInstances: Map[String, CalculatedWithHistoryFieldProcessor] = {
    foundationFields.filter(_.`type`() == FieldType.CUSTOM_WITH_HISTORY).map(field => {
      if (field.handler() == null) throw new StateCalculationConfigException(s"Field ${field.name} is defined as ${FieldType.CUSTOM_WITH_HISTORY} but no handler defined")
      val foundationFieldCreator = Class.forName(field.handler.clazz).newInstance().asInstanceOf[CalculatedWithHistoryFieldProcessor]
      field.name -> foundationFieldCreator
    }).toMap
  }

  private def createCustomFieldInstances: Map[String, CustomFieldProcessor] = {
    foundationFields.filter(_.`type`() == FieldType.CUSTOM).map(field => {
      if (field.handler() == null) throw new StateCalculationConfigException(s"Field ${field.name} is defined as ${FieldType.CUSTOM} but no handler defined")
      val foundationFieldCreator = Class.forName(field.handler.clazz).newInstance().asInstanceOf[CustomFieldProcessor]
      field.name -> foundationFieldCreator
    }).toMap
  }

}
