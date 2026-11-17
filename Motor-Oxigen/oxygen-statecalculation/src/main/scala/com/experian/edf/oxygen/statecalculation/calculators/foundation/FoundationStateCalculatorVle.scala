package com.experian.edf.oxygen.statecalculation.calculators.foundation

import java.util.{Map => JMap}
import com.experian.edf.oxygen.global.domain.statecalc.{SyntheticEvent, SyntheticEvents}
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils._
import com.experian.edf.oxygen.stage.statecalculation.calculators.StateCalculator
import com.experian.edf.oxygen.stage.statecalculation.calculators.foundation.FoundationFunctions._
import com.experian.edf.oxygen.stage.statecalculation.calculators.utils.CalculationUtils
import com.experian.edf.oxygen.stage.statecalculation.calculators.utils.CalculationUtils._
import com.experian.edf.oxygen.stage.statecalculation.config.foundation.{FieldType, FoundationField}
import com.experian.edf.oxygen.stage.statecalculation.exceptions.StateCalculationConfigException
import com.experian.edf.oxygen.stage.statecalculation.processors.foundation.{CalculatedWithHistoryFieldProcessor, CustomFieldProcessor}
import com.experian.edf.oxygen.stage.statecalculation.{FoundationRecord, FoundationRecordList, JavaFoundationRecord, StateCalculatorContext}

import javax.script.{ScriptEngine, ScriptEngineManager}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * @author Abraham, Thomas - c15627a
 */
class FoundationStateCalculatorVle(scContext: StateCalculatorContext, engine: ScriptEngine = new ScriptEngineManager().getEngineByName("nashorn")) extends StateCalculator(scContext, engine) {
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
      foundationRecord.put("parentEventIds", syntheticEvent.parentEventIds.asScala.toList)
      foundationFields.filter(_.`type`() == FieldType.DERIVED)
        .foreach(field => {
          val value = computeDerivedFoundationField(field, syntheticEvent).getOrElse(toDataType(field.dataType(), field.defaultValue))

          if (value != null && value!= false) {
            foundationRecord.put(field.name.intern(), value)
            addAliases(foundationRecord.asScala, field, value)
          }
        })
      foundationFields.filter(_.`type`() != FieldType.DERIVED)
        .foreach(field => {
          field.`type`() match {
            case FieldType.CALCULATED => computeCalculatedFields(foundationRecord.asScala, field)
            case FieldType.CUSTOM_WITH_HISTORY => computeCustomWithHistoryField(foundationStates, foundationRecord, field)
            case FieldType.CUSTOM => computeCustomField(foundationRecord.asScala, field)
            case FieldType.UNKNOWN => throw new StateCalculationConfigException(s"UNKNOWN is not a supported field Type for ${field.name()}. Please remove the same from the configuration. \n" +
              s"Allowed types are ${FieldType.values().toList.filter(!_.equals(FieldType.UNKNOWN)).mkString(", ")}")
          }
        })

      // TODO: CUSTOM
      foundationStates += foundationRecord
    })
    syntheticEvents.synthesizedEvents().clear()
    logger.debug(s"Foundation Time for ${syntheticEvents.pin}: " + (System.currentTimeMillis() - startTime) + " milliseconds")

    (foundationStates.map(_.asScala.toMap).toList,List.empty)
  }
  private def computeCustomWithHistoryField(foundationStates: ListBuffer[JavaFoundationRecord], foundationRecord: JavaFoundationRecord, field: FoundationField) = {
    val creator: CalculatedWithHistoryFieldProcessor = customWithHistoryFieldInstances(field.name)
    val value = computeCalculatedWithHistoryField(field, foundationRecord, foundationStates.toList, creator).getOrElse(toDataType(field.dataType(), field.defaultValue))
    if (value != null) {
      foundationRecord.put(field.name, value)
      //  addAliases(foundationRecord, field, value)
    }
  }

  private def computeCalculatedFields(foundationRecord: scala.collection.mutable.Map[String, Any], field: FoundationField): Unit = {

    val value = computeCalculatedField(invokableEngine, field, foundationRecord.asJava).getOrElse(toDataType(field.dataType(), field.defaultValue))

    if (value != null &&  value!= false) {
      foundationRecord.put(field.name.intern(), value)
      addAliases(foundationRecord, field, value)
    }
  }

  private def computeCustomField(foundationRecord: scala.collection.mutable.Map[String, Any], field: FoundationField): Unit = {
    val createdFieldValue = customFieldInstances(field.name).createField(foundationRecord.toMap)
    val value = Option(toDataType(field.dataType, createdFieldValue)).getOrElse(toDataType(field.dataType(), field.defaultValue))
    if (value != null) {
      foundationRecord.put(field.name.intern(), value)
      addAliases(foundationRecord, field, value)
    }
  }

  private def addAliases(foundationRecord: scala.collection.mutable.Map[String, Any], field: FoundationField, value: Any): Unit = {
    if (value != null && scContext.stateCalculationConfiguration.computeAliasInFoundation && field.aliases() != null && !field.aliases().isEmpty) field.aliases().asScala.foreach(alias => foundationRecord.put(alias.intern(), value))
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


  def computeFoundationStateVLE(sortedSyntheticEvents: List[SyntheticEvent], pin: Long): FoundationRecordList = {
    val startTime = System.currentTimeMillis()
    val foundationStates = new ListBuffer[FoundationRecord]

    sortedSyntheticEvents.foreach(syntheticEvent => {
      val foundationRecord = scala.collection.mutable.Map[String, Any]()

      foundationRecord.put("pin",pin)
      foundationRecord.put("dataSource", syntheticEvent.dataSource)
      foundationRecord.put("subscriber", syntheticEvent.subscriber)
      foundationRecord.put("subscriberRelation", syntheticEvent.subscriberRelation)
      foundationRecord.put("asOfPeriod", syntheticEvent.asOfPeriod().epochDateTime)
      foundationRecord.put("parentEventIds", syntheticEvent.parentEventIds.asScala.toList)

      foundationFields.filter(_.`type`() == FieldType.DERIVED)
        .foreach(field => {
          val value = computeDerivedFoundationField(field, syntheticEvent).getOrElse(toDataType(field.dataType(), field.defaultValue))
          if (value != null) {
            foundationRecord.put(field.name, value)
            addAliases(foundationRecord, field, value)
          }
        })
      foundationFields.filter(_.`type`() != FieldType.DERIVED)
        .foreach(field => {
          field.`type`() match {
            case FieldType.CALCULATED => computeCalculatedFields(foundationRecord, field)
            case FieldType.CUSTOM => computeCustomField(foundationRecord, field)
            case FieldType.UNKNOWN => throw new StateCalculationConfigException(s"UNKNOWN is not a supported field Type for ${field.name()}. Please remove the same from the configuration. \n" +
              s"Allowed types are ${FieldType.values().toList.filter(!_.equals(FieldType.UNKNOWN)).mkString(", ")}")
          }
        })


      foundationStates += foundationRecord.toMap
    })
    logger.debug(s"Foundation Time for ${pin}: " + (System.currentTimeMillis() - startTime) + " milliseconds")

    foundationStates.toList
  }



  def computeFoundationStateVLEGlobalEvents(sortedSyntheticEvents: List[SyntheticEvent], pin: Long): (FoundationRecordList,FoundationRecordList) = {
    val startTime = System.currentTimeMillis()
    val foundationStates = new ListBuffer[FoundationRecord]
    val foundationStatesGlobal = new ListBuffer[FoundationRecord]

    sortedSyntheticEvents.foreach(syntheticEvent => {
      val foundationRecord = scala.collection.mutable.Map[String, Any]()

      foundationRecord.put("pin",pin)
      foundationRecord.put("dataSource", syntheticEvent.dataSource)
      foundationRecord.put("subscriber", syntheticEvent.subscriber)
      foundationRecord.put("subscriberRelation", syntheticEvent.subscriberRelation)
      foundationRecord.put("asOfPeriod", syntheticEvent.asOfPeriod().epochDateTime)
      foundationRecord.put("parentEventIds", syntheticEvent.parentEventIds.asScala.toList)
      foundationFields.filter(_.`type`() == FieldType.DERIVED)
        .foreach(field => {
          val value = computeDerivedFoundationField(field, syntheticEvent).getOrElse(toDataType(field.dataType(), field.defaultValue))
          if (value != null) {
            foundationRecord.put(field.name, value)
            addAliases(foundationRecord, field, value)
          }
        })
      foundationFields.filter(_.`type`() != FieldType.DERIVED)
        .foreach(field => {
          field.`type`() match {
            case FieldType.CALCULATED => computeCalculatedFields(foundationRecord, field)
            case FieldType.CUSTOM => computeCustomField(foundationRecord, field)
            case FieldType.UNKNOWN => throw new StateCalculationConfigException(s"UNKNOWN is not a supported field Type for ${field.name()}. Please remove the same from the configuration. \n" +
              s"Allowed types are ${FieldType.values().toList.filter(!_.equals(FieldType.UNKNOWN)).mkString(", ")}")
          }
        })
      // TODO: CUSTOM
      if (isGlobalEventPredicate(foundationRecord))
        foundationStatesGlobal+= foundationRecord.toMap
      else
        foundationStates += foundationRecord.toMap
    })
    logger.debug(s"Foundation Time for ${pin}: " + (System.currentTimeMillis() - startTime) + " milliseconds")
    logger.debug(s"[ GlobalEvents ${foundationStatesGlobal.size} ,  foundationEvents ${foundationStates.size} ]")

    (foundationStates.toList,foundationStatesGlobal.toList)
  }

  def isGlobalEventPredicate(foundationRecord:  mutable.Map[String, Any]) : Boolean = {
    foundationRecord.contains("isOfficialSource") || foundationRecord.contains("isBDACSV") ||
      foundationRecord.contains("isBDIIREGI")
  }

}