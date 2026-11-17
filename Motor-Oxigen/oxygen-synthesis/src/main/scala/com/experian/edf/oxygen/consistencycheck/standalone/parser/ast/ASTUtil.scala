package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import com.experian.edf.oxygen.consistencycheck.domain._
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.{ExpressionConstant, StateCheck}
import com.experian.edf.oxygen.domain.internal.{Frequency, Period, PeriodUnit}
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.utils.JsonUtils
import org.slf4j.LoggerFactory.getLogger
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable.ListBuffer

/**
 * Utils for AST
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
private[standalone] object ASTUtil extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  def getValueFromSyntheticEvent[T](event: SyntheticEvent, domain: String, elementName: String, elementType: String): T = {
    if (isDomainGlobal(domain)) {
      getValueForGlobalTermFromSyntheticEvent[T](event, elementName, elementType)
    }
    else {
      val elementValue = if (event.curatedRecordJson.containsKey(elementName))
        event.curatedRecordJson.get(elementName)
      else null
      if (elementValue != null) { //&& elementValue.isDefined) {
        elementType match {
          case ExpressionConstant.AsInt => elementValue.toString.toDouble.toInt.asInstanceOf[T]
          case ExpressionConstant.AsString => elementValue.asInstanceOf[T]
          case ExpressionConstant.AsDouble => elementValue.asInstanceOf[T]
          case ExpressionConstant.AsBoolean => elementValue.toString.toBoolean.asInstanceOf[T]
          case ExpressionConstant.AsLong => {
            elementValue match {
              case elementValue: String => elementValue.toString.toLong.asInstanceOf[T]
              case _ => elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]

            }
          }
          case ExpressionConstant.AsDateFormat => elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]
          case ExpressionConstant.AsTimestampFromString => elementValue.asInstanceOf[String].asInstanceOf[T]
          case ExpressionConstant.AsPeriod => getPeriodForSyntheticElement(event.asOfPeriod.unit, elementValue.asInstanceOf[Number].longValue()).asInstanceOf[T]
          case ExpressionConstant.AsJsonArray => throw new NotImplementedException
          case ExpressionConstant.AsJsonObject => throw new NotImplementedException
          // TODO: period should be derived from frequency
          case _ => throw new NoSuchElementException
        }
      } else
        null.asInstanceOf[T]
    }
  }


  /**
   *
   * @param event
   * @param elementName
   * @param elementType
   * @tparam T
   * @return
   */
  def getValueForTermFromTaggedCuratedEvent[T](event: TaggedCuratedEventWithMap, elementName: String, elementType: String): T = {
    val elementValue = if (event.curatedRecordJson.containsKey(elementName)) {
      event.curatedRecordJson.get(elementName)
    } else if (event.curatedRecordJson.containsKey(TaggedCuratedEvent.getCorrectedFieldName(elementName))) {
      event.curatedRecordJson.get(TaggedCuratedEvent.getCorrectedFieldName(elementName))
    } else null
    // change this to get from
    if (elementValue != null && elementValue != None) {
      elementType match {
        case ExpressionConstant.AsInt => elementValue.toString.toDouble.toInt.asInstanceOf[T]
        case ExpressionConstant.AsString => elementValue.toString.asInstanceOf[T]
        case "asNumber" => elementValue.asInstanceOf[Number].asInstanceOf[T]
        case ExpressionConstant.AsDouble => elementValue.asInstanceOf[Double].asInstanceOf[T]
        case ExpressionConstant.AsBoolean => elementValue.toString.toBoolean.asInstanceOf[T]
        case ExpressionConstant.AsLong => {
          elementValue match {
            case elementValue: String => elementValue.toString.toLong.asInstanceOf[T]
            case _ => elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]

          }
        }
        case ExpressionConstant.AsDateFormat => elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]
        case ExpressionConstant.AsTimestampFromString => elementValue.asInstanceOf[String].asInstanceOf[T]
        case ExpressionConstant.AsPeriod => getPeriodForElement(event.minFrequency, elementValue.asInstanceOf[Number].longValue()).asInstanceOf[T]
        // TODO: period should be derived from frequency
        case _ => throw new NoSuchElementException

      }
    } else
      null.asInstanceOf[T]
  }

  def getAnyValueForTermFromTaggedCuratedEvent(event: TaggedCuratedEventWithMap, elementName: String): Any = {
    if (event.curatedRecordJson.containsKey(elementName)) {
      event.curatedRecordJson.get(elementName)
    } else if (event.curatedRecordJson.containsKey(TaggedCuratedEvent.getCorrectedFieldName(elementName))) {
      event.curatedRecordJson.get(TaggedCuratedEvent.getCorrectedFieldName(elementName))
    } else if (event.curatedRecordJson.containsKey(getNestedFieldName(elementName)._1)) {
      val (domain, filedName) = getNestedFieldName(elementName)
      event.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].getOrElse(filedName,null)
    }
    else null
  }

  def getAnyValueForGlobalTermFromTaggedCuratedEvent(event: TaggedCuratedEventWithMap, elementName: String): Any = {
    elementName match {
      case "subscriber" => event.subscriber
      case "subscriberRelation" => event.subscriberRelation
      case "dataSource" => event.lineage.dataSource
      case "asOfDate" => event.asOfDate
      case "asOfPeriod" => event.asOfPeriod
      case "receivedDate" => event.lineage.receivedDate
      case "subscriberRelationStartDate" => event.subscriberRelationStartDate
      case "subscriberRelationEndDate" => event.subscriberRelationEndDate
      case "eventId" => event.eventId
      case _ => throw new NoSuchElementException
      // TODO: subscriberstart and end date remove
    }
  }

  def isDomainGlobal(domain: String): Boolean = {
    domain.toLowerCase == "global"
  }

  def doesGlobalTermExist(event: TaggedCuratedEventWithMap, termName: String): Boolean = {
    termName match {
      case "subscriberRelationStartDate" => if (event.subscriberRelationStartDate <= 0) false else true
      case "subscriberRelationEndDate" => if (event.subscriberRelationEndDate <= 0) false else true
      case _ => true
    }
  }

  def getValueForGlobalTermFromTaggedCuratedEvent[T](event: TaggedCuratedEventWithMap, elementName: String, elementType: String): T = {
    elementName match {
      case "subscriber" => event.subscriber.asInstanceOf[T]
      case "subscriberRelation" => event.subscriberRelation.asInstanceOf[T]
      case "dataSource" => event.lineage.dataSource.asInstanceOf[T]
      case "asOfDate" => event.asOfDate.asInstanceOf[T]
      case "asOfPeriod" => event.asOfPeriod.asInstanceOf[T]
      case "receivedDate" => event.lineage.receivedDate.asInstanceOf[T]
      case "subscriberRelationStartDate" => event.subscriberRelationStartDate.asInstanceOf[T]
      case "subscriberRelationEndDate" => event.subscriberRelationEndDate.asInstanceOf[T]
      case "eventId" => event.eventId.asInstanceOf[T]
      case _ => throw new NoSuchElementException
      // TODO: subscriberstart and end date remove
    }
  }

  def getValueForGlobalTermFromSyntheticEvent[T](event: SyntheticEvent, elementName: String, elementType: String): T = {
    elementName match {
      case "subscriber" => event.subscriber.asInstanceOf[T]
      case "subscriberRelation" => event.subscriberRelation.asInstanceOf[T]
      case "dataSource" => event.dataSource.asInstanceOf[T]
      case "asOfPeriod" => event.asOfPeriod.asInstanceOf[T]
      case _ => throw new NoSuchElementException
    }
  }

  def getAnyValueForGlobalTermFromSyntheticEvent(event: SyntheticEvent, elementName: String): Any = {
    elementName match {
      case "subscriber" => event.subscriber
      case "subscriberRelation" => event.subscriberRelation
      case "dataSource" => event.dataSource
      case "asOfPeriod" => event.asOfPeriod
      case _ => throw new NoSuchElementException
    }
  }

  def getAnyValueForTermFromSyntheticEvent(event: SyntheticEvent, elementName: String): Any = {
    val elementValue = if (event.curatedRecordJson.containsKey(elementName))
        event.curatedRecordJson.get(elementName)
      else if (event.curatedRecordJson.containsKey(getNestedFieldName(elementName)._1)) {
        val (domain, filedName) = getNestedFieldName(elementName)
        event.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].getOrElse(filedName,null)
    }
    else null
    elementValue
  }


  def compareValues(a: Any, b: Any, comparator: String, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        case DomainDictionaryInternal.IntType => compareInt(JsonUtils.fromJson(a.toString, classOf[Double]).toInt, JsonUtils.fromJson(b.toString, classOf[Double]).toInt, comparator)
        case DomainDictionaryInternal.StringType => compareString(a.toString, b.toString, comparator)
        case DomainDictionaryInternal.DoubleType => compareDouble(a.asInstanceOf[Double], b.asInstanceOf[Double], comparator)
        case DomainDictionaryInternal.BooleanType => compareBoolean(a.asInstanceOf[Boolean], b.asInstanceOf[Boolean], comparator)
        case DomainDictionaryInternal.LongType => compareLong(a.asInstanceOf[Number].longValue(), b.asInstanceOf[Number].longValue(), comparator)
        case ExpressionConstant.AsDateFormat => compareString(a.asInstanceOf[String], b.asInstanceOf[String], comparator)
        case ExpressionConstant.AsTimestampFromString => compareLong(a.asInstanceOf[Number].longValue(), b.asInstanceOf[Number].longValue(), comparator)
        case DomainDictionaryInternal.PeriodType => comparePeriod(a.asInstanceOf[Period], b.asInstanceOf[Period], comparator)
        case DomainDictionaryInternal.IntegerType => compareInt(JsonUtils.fromJson(a.toString, classOf[Double]).toInt, JsonUtils.fromJson(b.toString, classOf[Double]).toInt, comparator)
        case _ => throw new NoSuchElementException

      }
    // TODO: null conversions
  }

  def isEqual(a: Any, b: Any, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        case DomainDictionaryInternal.StringType => a.toString == b.toString
        case DomainDictionaryInternal.IntType => a.asInstanceOf[Number].intValue() == b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.IntegerType => a.asInstanceOf[Number].intValue() == b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.DoubleType => a.asInstanceOf[Number].doubleValue() == b.asInstanceOf[Number].doubleValue()
        case DomainDictionaryInternal.LongType => a.asInstanceOf[Number].doubleValue().longValue() == b.asInstanceOf[Number].doubleValue().longValue()
        case ExpressionConstant.AsDateFormat => a.asInstanceOf[String] == b.asInstanceOf[String]
        case ExpressionConstant.AsTimestampFromString => a.asInstanceOf[Number].longValue() == b.asInstanceOf[Number].longValue()
        case DomainDictionaryInternal.PeriodType => a.asInstanceOf[Period] == b.asInstanceOf[Period]
        case _ => throw new NoSuchElementException
      }
  }

  def isNotEqual(a: Any, b: Any, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        case DomainDictionaryInternal.StringType => a.toString != b.toString
        case DomainDictionaryInternal.IntType => a.asInstanceOf[Number].intValue() != b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.DoubleType => a.asInstanceOf[Number].doubleValue() != b.asInstanceOf[Number].doubleValue()
        case DomainDictionaryInternal.LongType => a.asInstanceOf[Number].longValue() != b.asInstanceOf[Number].longValue()
        case ExpressionConstant.AsDateFormat => a.asInstanceOf[String] != b.asInstanceOf[String]
        case ExpressionConstant.AsTimestampFromString => a.asInstanceOf[Number].longValue() != b.asInstanceOf[Number].longValue()
        case DomainDictionaryInternal.PeriodType => a.asInstanceOf[Period] != b.asInstanceOf[Period]
        case DomainDictionaryInternal.IntegerType => a.asInstanceOf[Number].intValue() != b.asInstanceOf[Number].intValue()
        case _ => throw new NoSuchElementException
      }
  }

  def isGreaterThan(a: Any, b: Any, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        case DomainDictionaryInternal.IntType => a.asInstanceOf[Number].intValue() > b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.DoubleType => a.asInstanceOf[Number].doubleValue() > b.asInstanceOf[Number].doubleValue()
        case DomainDictionaryInternal.LongType => a.asInstanceOf[Number].longValue() > b.asInstanceOf[Number].longValue()
        case ExpressionConstant.AsDateFormat => a.asInstanceOf[String] > b.asInstanceOf[String]
        case ExpressionConstant.AsTimestampFromString => a.asInstanceOf[Number].longValue() > b.asInstanceOf[Number].longValue()
        case DomainDictionaryInternal.PeriodType => a.asInstanceOf[Period] > b.asInstanceOf[Period]
        case DomainDictionaryInternal.IntegerType => a.asInstanceOf[Number].intValue() > b.asInstanceOf[Number].intValue()
        case _ => throw new NoSuchElementException
      }
  }

  def isGreaterThanOrEquals(a: Any, b: Any, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        // TODO: test these
        case DomainDictionaryInternal.IntType => a.asInstanceOf[Number].intValue() >= b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.DoubleType => a.asInstanceOf[Number].doubleValue() >= b.asInstanceOf[Number].doubleValue()
        case DomainDictionaryInternal.LongType => a.asInstanceOf[Number].doubleValue().longValue() >= b.asInstanceOf[Number].doubleValue().longValue()
        case ExpressionConstant.AsDateFormat => a.asInstanceOf[String] >= b.asInstanceOf[String]
        case ExpressionConstant.AsTimestampFromString => a.asInstanceOf[Number].longValue() >= b.asInstanceOf[Number].longValue()
        case DomainDictionaryInternal.PeriodType => a.asInstanceOf[Period] >= b.asInstanceOf[Period]
        case DomainDictionaryInternal.IntegerType => a.asInstanceOf[Number].intValue() >= b.asInstanceOf[Number].intValue()
        case _ => throw new NoSuchElementException
      }
  }

  def isLessThan(a: Any, b: Any, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        case DomainDictionaryInternal.IntType => a.asInstanceOf[Number].intValue() < b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.DoubleType => a.asInstanceOf[Number].doubleValue() < b.asInstanceOf[Number].doubleValue()
        case DomainDictionaryInternal.LongType => a.asInstanceOf[Number].longValue() < b.asInstanceOf[Number].longValue()
        case ExpressionConstant.AsDateFormat => a.asInstanceOf[String] < b.asInstanceOf[String]
        case ExpressionConstant.AsTimestampFromString => a.asInstanceOf[Number].longValue() < b.asInstanceOf[Number].longValue()
        case DomainDictionaryInternal.PeriodType => a.asInstanceOf[Period] < b.asInstanceOf[Period]
        case DomainDictionaryInternal.IntegerType => a.asInstanceOf[Number].intValue() < b.asInstanceOf[Number].intValue()
        case _ => throw new NoSuchElementException
      }
  }

  def isLessThanOrEquals(a: Any, b: Any, valueType: String): Boolean = {
    if (a == null && b == null)
      true
    else if (a == null || b == null)
      false
    else
      valueType match {
        case DomainDictionaryInternal.IntType => a.asInstanceOf[Number].intValue() <= b.asInstanceOf[Number].intValue()
        case DomainDictionaryInternal.DoubleType => a.asInstanceOf[Number].doubleValue() <= b.asInstanceOf[Number].doubleValue()
        case DomainDictionaryInternal.LongType => a.asInstanceOf[Number].longValue() <= b.asInstanceOf[Number].longValue()
        case ExpressionConstant.AsDateFormat => a.asInstanceOf[String] <= b.asInstanceOf[String]
        case ExpressionConstant.AsTimestampFromString => a.asInstanceOf[Number].longValue() <= b.asInstanceOf[Number].longValue()
        case DomainDictionaryInternal.PeriodType => a.asInstanceOf[Period] <= b.asInstanceOf[Period]
        case DomainDictionaryInternal.IntegerType => a.asInstanceOf[Number].intValue() <= b.asInstanceOf[Number].intValue()
        case _ => throw new NoSuchElementException
      }
  }

  def compareInt(a: Int, b: Int, comparator: String): Boolean = {
    comparator match {
      case "==" => a == b
      case ">" => a > b
      case "<" => a < b
      case ">=" => a >= b
      case "<=" => a <= b
      case "!=" => a != b
      case _ => {
        throw new NoSuchElementException
        false
      }
    }
  }

  def compareLong(a: Long, b: Long, comparator: String): Boolean = {
    comparator match {
      case "==" => a == b
      case ">" => a > b
      case "<" => a < b
      case ">=" => a >= b
      case "<=" => a <= b
      case "!=" => a != b
      case _ => {
        throw new NoSuchElementException
        false
      }
    }
  }

  def compareDouble(a: Double, b: Double, comparator: String): Boolean = {
    comparator match {
      case "==" => a == b
      case ">" => a > b
      case "<" => a < b
      case ">=" => a >= b
      case "<=" => a <= b
      case "!=" => a != b
      case _ => {
        throw new NoSuchElementException
        false
      }
    }
  }

  def compareString(a: String, b: String, comparator: String): Boolean = {
    comparator match {
      case "==" => a == b
      case ">" => a > b
      case "<" => a < b
      case ">=" => a >= b
      case "<=" => a <= b
      case "!=" => a != b
      case _ => {
        throw new NoSuchElementException
        false
      }
    }
  }

  def comparePeriod(a: Period, b: Period, comparator: String): Boolean = {
    comparator match {
      case "==" => a == b
      case ">" => a > b
      case "<" => a < b
      case ">=" => a >= b
      case "<=" => a <= b
      case "!=" => a != b
      case _ => {
        throw new NoSuchElementException
        false
      }
    }
  }

  def compareBoolean(a: Boolean, b: Boolean, comparator: String): Boolean = {
    comparator match {
      case "==" => a == b
      case "!=" => a != b
      case _ => {
        throw new NoSuchElementException
        false
      }
    }
  }

  def applyOperation(oper: String, termType: String, a: Any, b: Any): Any = {
    termType match {
      case ExpressionConstant.AsInt => {
        oper match {
          case "+" => getIntValue(a) + getIntValue(b)
          case "-" => getIntValue(a) - getIntValue(b)
          case "*" => getIntValue(a) * getIntValue(b)
          case "/" => getIntValue(a) / getIntValue(b)
          case _ => {
            throw new IllegalArgumentException
            null
          }
        }
      }
      case ExpressionConstant.AsString =>
        oper match {
          case "+" => a.asInstanceOf[String] + b.asInstanceOf[String]
          case _ => {
            throw new IllegalArgumentException
            null
          }
        }
      case ExpressionConstant.AsDouble => {
        oper match {
          case "+" => getDoubleValue(a) + getDoubleValue(b)
          case "-" => getDoubleValue(a) - getDoubleValue(b)
          case "*" => getDoubleValue(a) * getDoubleValue(b)
          case "/" => getDoubleValue(a) / getDoubleValue(b)
          case _ => {
            throw new IllegalArgumentException
            null
          }
        }
      }
      case ExpressionConstant.AsBoolean => {
        throw new IllegalArgumentException
        null
      }
      case ExpressionConstant.AsLong => {
        oper match {
          case "+" => getLongValue(a) + getLongValue(b)
          case "-" => getLongValue(a) - getLongValue(b)
          case "*" => getLongValue(a) * getLongValue(b)
          case "/" => getLongValue(a) / getLongValue(b)
          case _ => {
            throw new IllegalArgumentException
            null
          }
        }
      }
      case ExpressionConstant.AsPeriod => {
        oper match {
          case "+" => a.asInstanceOf[Period].plus(getIntValue(b))
          case "-" => a.asInstanceOf[Period].minus(getIntValue(b))
          case _ => {
            throw new IllegalArgumentException
            null
          }
        }
      }
      case ExpressionConstant.AsJsonObject => throw new NotImplementedException
      case ExpressionConstant.AsJsonArray => throw new NotImplementedException
    }
  }

  def getIntValue(x: Any): Int = {
    x match {
      case (i: Int) => i
      case (i: Double) => i.toInt
      case (i: Long) => i.toInt
    }
  }

  def getDoubleValue(x: Any): Double = {
    x match {
      case (i: Int) => i.toDouble
      case (i: Double) => i
      case (i: Long) => i.toDouble
    }
  }

  def getLongValue(x: Any): Long = {
    x match {
      case (i: Int) => i.toLong
      case (i: Double) => i.toLong
      case (i: Long) => i
    }
  }


  def getPeriodForSyntheticElement(periodUnit: PeriodUnit, elementValue: Long): Period = {
    val x = periodUnit
    periodUnit.toString match {
      case "Year" => Period.yearlyPeriodFromEpochDateTime(elementValue)
      case "QuarterOfYear" => Period.quarterlyPeriodFromEpochDateTime(elementValue)
      case "MonthOfYear" => Period.monthlyPeriodFromEpochDateTime(elementValue)
      case "WeekOfYear" => Period.weeklyPeriodFromEpochDateTime(elementValue)
      case "DayOfYear" => Period.dailyPeriodFromEpochDateTime(elementValue)
      case "PeriodNone" => Period.nonePeriod(elementValue)
    }
  }

  def getPeriodForElement(minFrequency: Frequency, elementValue: Long): Period = {
    if (minFrequency.isEqual(Frequency.none)) {
      Period.nonePeriod(elementValue)
    } else {
      minFrequency.unit match {
        case Frequency.EveryNMonth => Period.monthlyPeriodFromEpochDateTime(elementValue)
        case Frequency.EveryNQuarter => Period.quarterlyPeriodFromEpochDateTime(elementValue)
        case Frequency.EveryNDay => Period.dailyPeriodFromEpochDateTime(elementValue)
        case Frequency.EveryNWeek => Period.weeklyPeriodFromEpochDateTime(elementValue)
        case Frequency.EveryNYear => Period.yearlyPeriodFromEpochDateTime(elementValue)
        case Frequency.NA => Period.nonePeriod(elementValue)
        case _ => Period.nonePeriod(elementValue)

      }
    }
  }

  private[standalone] def getDSRKey(ds: String, sub: String, rel: String): Int = {
    (ds + ":" + sub + ":" + rel).hashCode
  }

  private[standalone] def getSRKey(sub: String, rel: String): Int = {
    (sub + ":" + rel).hashCode
  }

  def doesTermExistInTaggedCuratedEvent(event: TaggedCuratedEventWithMap, elementName: String): Boolean = {
    event.curatedRecordJson.containsKey(elementName) || event.curatedRecordJson.containsKey(TaggedCuratedEvent.getCorrectedFieldName(elementName)) || {
        val (domain, filedName) = getNestedFieldName(elementName)
        event.curatedRecordJson.containsKey(domain) && event.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].contains(filedName)
      }
  }

  def doesTermExistInSyntheticEvent(event: SyntheticEvent, elementName: String): Boolean = {
    event.curatedRecordJson.containsKey(elementName) ||  {
      val (domain, filedName) = getNestedFieldName(elementName)
      event.curatedRecordJson.containsKey(domain) && event.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].contains(filedName)
    }
  }

  def getFieldTypeFromDomainDictionary(fieldName: String, domainDictionary: DomainDictionaryInternal): String = {
    fieldName match {
      case "subscriber" => DomainDictionaryInternal.StringType
      case "subscriberRelation" => DomainDictionaryInternal.StringType
      case "dataSource" => DomainDictionaryInternal.StringType
      case "asOfDate" => DomainDictionaryInternal.LongType
      case "asOfPeriod" => DomainDictionaryInternal.PeriodType
      case "receivedDate" => DomainDictionaryInternal.LongType
      case "subscriberRelationStartDate" => DomainDictionaryInternal.LongType
      case "subscriberRelationEndDate" => DomainDictionaryInternal.LongType
      case "eventId" => DomainDictionaryInternal.LongType
      case _ => {
        if (domainDictionary.fieldsMap.contains(fieldName))
          domainDictionary.fieldsMap(fieldName).fieldType
        else if (domainDictionary.fieldsMap.contains(getNestedFieldName(fieldName)._1) ) {
          val (domain, elementName) = getNestedFieldName(fieldName)
          //complex datatype
          val fields = domainDictionary.fieldsMap(domain).complexTypeDefinition.getFields
          val schemaField = fields.filter(f=>f.getName == elementName)
          if(schemaField.isEmpty) DomainDictionaryInternal.StringType else schemaField.head.getDataType.identifier()
        } else
          DomainDictionaryInternal.StringType
      }
      // TODO: get just fieldsMap and throw exception if field not found
    }
  }


  def getMostRecentForTermFromSyntheticEvent(fieldName: String, thatEvents: List[SyntheticEvent]): Any = {
    // TODO: should we traverse reverse
    val sortedThatEvents = thatEvents.asInstanceOf[List[SyntheticEvent]].sortBy(_.asOfPeriod)(Ordering[Period].reverse)
    //val sortedThatEvents = thatEvents.reverse
    sortedThatEvents.foreach(e => {
      if (e.curatedRecordJson.containsKey(fieldName)) {
        return e.curatedRecordJson.get(fieldName)
      } else if (e.curatedRecordJson.containsKey(getNestedFieldName(fieldName)._1)) {
        val (domain, filedName) = getNestedFieldName(fieldName)
        return e.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].getOrElse(filedName,null)
      }
    })
    null
  }

  def getMostRecentForGlobalTermFromSyntheticEvent(fieldName: String, thatEvents: List[SyntheticEvent]): Any = {
    // TODO: sortedEventsJustOnce
    val sortedThatEvents = thatEvents.asInstanceOf[List[SyntheticEvent]].sortBy(_.asOfPeriod)(Ordering[Period].reverse)
    //val sortedThatEvents = thatEvents.reverse
    sortedThatEvents.foreach(e => {
      return getAnyValueForGlobalTermFromSyntheticEvent(e, fieldName)
    })
    null
  }

  def getMostRecentForTermFromTaggedCuratedEvent(fieldName: String, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    // TODO: sortedEventsJustOnce
    val sortedThatEvents = thatEvents.asInstanceOf[List[TaggedCuratedEventWithMap]].sortBy(_.asOfPeriod)(Ordering[Period].reverse)
    //val sortedThatEvents = thatEvents.reverse
    sortedThatEvents.foreach(e => {
      if (e.curatedRecordJson.containsKey(fieldName))
        return e.curatedRecordJson.get(fieldName)
      else if (e.curatedRecordJson.containsKey(getNestedFieldName(fieldName)._1)) {
        val (domain, elementName) = getNestedFieldName(fieldName)
        return e.curatedRecordJson.get(domain).asInstanceOf[Map[String, Any]].getOrElse(elementName, null)
      }
    })
    null
  }

  def getMostRecentForGlobalTermFromTaggedCuratedEvent(fieldName: String, thatEvents: List[TaggedCuratedEventWithMap]): Any = {
    // TODO: sortedEventsJustOnce
    val sortedThatEvents = thatEvents.asInstanceOf[List[TaggedCuratedEventWithMap]].sortBy(_.asOfPeriod)(Ordering[Period].reverse)
    //val sortedThatEvents = thatEvents.reverse
    sortedThatEvents.foreach(e => {
      return getAnyValueForGlobalTermFromTaggedCuratedEvent(e, fieldName)
    })
    null
  }

  /**
   * Lookup util method to retrieve list of values from the table.
   *
   * @param tableName   : name of the table
   * @param lookUpBy    : Map of key name and its value to lookup
   * @param selectField : Field in the table to be filtered
   * @return list of values filtered based on the selected field
   */
  def getExternalStatus(tableName: String,
                        lookUpBy: Map[String, String],
                        selectField: String,
                        lookupService: OxygenLookup): List[String] = {
    val valueList = lookupService.get(tableName, lookUpBy)
    val resultMap = valueList.map(resultMap => resultMap.map(y => y.get(selectField)))
    val resultList = ListBuffer[String]()
    resultMap match {
      case Some(s) => s.foreach(f =>
        if (f != null && f != None && f.get != null) {
          resultList += f.get.toString
        }
        else {
          resultList += ""
        })
      case None => resultList += ""
    }
    resultList.toList
  }

  def checkStateOpen(fieldName: String, stateId: String, thatName: String, thisEvent: TaggedCuratedEventWithMap, stateCheck: StateCheck): Boolean = {

    val thisEventValue = ASTUtil.getAnyValueForGlobalTermFromTaggedCuratedEvent(thisEvent, thatName).asInstanceOf[Long]

    val stateCheckResult = try {
      val key = stateCheck.getSRSKey(fieldName, stateId, thisEvent.subscriber, thisEvent.subscriberRelation)
      val stateFieldList = stateCheck.getStateFieldsList(fieldName)
      val newResult = stateCheck.isNewStateOpen(stateFieldList, fieldName, thisEventValue, key)
      newResult
    } catch {
      case e: Exception => {
        logger.error(s"Error on Checking {}, StateName {}, StateId {}, with a message of {} ", SynthesisConstants.SHOULD_ACCEPT_STATE, fieldName, stateId, e.getMessage)
        true
      }
    }
    stateCheckResult
  }

  def getNestedFieldName(fieldName: String): (String, String) = {
    val str = fieldName.split("_")
    if(str.length == 2) (str(0), str(1))
    else ("", "")
  }
}
