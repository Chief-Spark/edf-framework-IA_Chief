package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.domain.HeterogeneousCollection
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.parser.base.{ConsistencyParserEvents, ParserSyntheticEvent, ParserTaggedCuratedEvent}
import com.experian.edf.oxygen.domain.internal.{Frequency, Period, PeriodUnit}
import com.experian.edf.oxygen.utils.JsonUtils
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import java.text.SimpleDateFormat
import java.util.Locale
import scala.reflect.runtime.universe._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 *
 * @author annapurna - C22603A
 *         Created on 3/23/20 at 9:19 AM
 */
private[oxygen] object Util extends Serializable {

  def getValueFromSyntheticEvent[T](event: SyntheticEvent, domain: String, elementName: String, elementType: String): T = {
    if (isDomainGlobal(domain)) {
      getValueForGlobalTermFromSyntheticEvent[T](event, elementName, elementType)
    }
    else {
      val elementValue =
        if (event.curatedRecordJson.containsKey(elementName)) {
          event.curatedRecordJson.get(elementName)
        } else if(event.curatedRecordJson.containsKey(domain)){
          event.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].getOrElse(elementName,null)
        }
        else null
      if (elementValue != null ){ //&& elementValue.isDefined) {
        elementType match {
          case ExpressionConstant.AsInt => elementValue.toString.toDouble.toInt.asInstanceOf[T]
          case ExpressionConstant.AsString => elementValue.asInstanceOf[T]
          case ExpressionConstant.AsDouble => elementValue.asInstanceOf[T]
          case ExpressionConstant.AsBoolean => elementValue.toString.toBoolean.asInstanceOf[T]
          case ExpressionConstant.AsLong => {  elementValue match {
            case elementValue:String => elementValue.toString.toLong.asInstanceOf[T]
            case _ =>             elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]

          }
          }
          case ExpressionConstant.AsDateFormat => elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]
          case ExpressionConstant.AsTimestampFromString => elementValue.asInstanceOf[String].asInstanceOf[T]
          case ExpressionConstant.AsPeriod => getPeriodForSyntheticElement(event.asOfPeriod.unit,elementValue.asInstanceOf[Number].longValue()).asInstanceOf[T]
          case ExpressionConstant.AsJsonArray => throw new NotImplementedException
          case ExpressionConstant.AsJsonObject => throw new NotImplementedException
          // TODO: period should be derived from frequency
          case _ => throw new NoSuchElementException
        }
      } else
        null.asInstanceOf[T]
    }
  }

  def getValueFromTaggedCuratedEvent[T](event: TaggedCuratedEventWithMap, domain: String, elementName: String, elementType: String): T = {
    if (isDomainGlobal(domain)) {
      getValueForGlobalTermFromTaggedCuratedEvent[T](event, elementName, elementType)
    }
    else {
      val elementValue = if (event.curatedRecordJson.containsKey(elementName)) {
        event.curatedRecordJson.get(elementName)
      } else if (event.curatedRecordJson.containsKey(TaggedCuratedEvent.getCorrectedFieldName(elementName))) {
        event.curatedRecordJson.get(TaggedCuratedEvent.getCorrectedFieldName(elementName))
      } else if (event.curatedRecordJson.containsKey(domain)) {
        event.curatedRecordJson.get(domain).asInstanceOf[Map[String,Any]].getOrElse(elementName,null)
      }
      else null

      if (elementValue != null && elementValue !=None) {
        elementType match {
          case ExpressionConstant.AsInt => elementValue.toString.toDouble.toInt.asInstanceOf[T]
          case ExpressionConstant.AsString => elementValue.toString.asInstanceOf[T]
          case ExpressionConstant.AsDouble => elementValue.asInstanceOf[Double].asInstanceOf[T]
          case ExpressionConstant.AsBoolean => elementValue.toString.toBoolean.asInstanceOf[T]
          case ExpressionConstant.AsLong => {  elementValue match {
            case elementValue:String => elementValue.toString.toLong.asInstanceOf[T]
            case _ =>             elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]

          }
          }
          case ExpressionConstant.AsDateFormat => elementValue.asInstanceOf[Number].longValue().asInstanceOf[T]
          case ExpressionConstant.AsTimestampFromString => elementValue.asInstanceOf[String].asInstanceOf[T]
          case ExpressionConstant.AsPeriod => getPeriodForElement(event.minFrequency, elementValue.asInstanceOf[Number].longValue()).asInstanceOf[T]
          case ExpressionConstant.AsJsonArray => throw new NotImplementedException
          case ExpressionConstant.AsJsonObject => throw new NotImplementedException
          // TODO: period should be derived from frequency
          case _ => throw new NoSuchElementException

        }
      } else
        null.asInstanceOf[T]
    }
  }

  def isDomainGlobal(domain: String): Boolean = {
    domain.toLowerCase == "global"
  }

  def doesGlobalTermExist(event: TaggedCuratedEvent, termName: String): Boolean = {
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

  def compareValues(a: Any, b: Any, comparator: String, valueType: String): Boolean = {
    if (a==null && b == null)
      true
    else if (a == null || b == null)
      false
    else
    valueType match {
      case ExpressionConstant.AsInt => compareInt(JsonUtils.fromJson(a.toString, classOf[Double]).toInt, JsonUtils.fromJson(b.toString, classOf[Double]).toInt, comparator)
      case ExpressionConstant.AsString => compareString(a.toString, b.toString, comparator)
      case ExpressionConstant.AsDouble => compareDouble(a.asInstanceOf[Double], b.asInstanceOf[Double], comparator)
      case ExpressionConstant.AsBoolean => compareBoolean(a.asInstanceOf[Boolean], b.asInstanceOf[Boolean], comparator)
      case ExpressionConstant.AsLong => compareLong(a.asInstanceOf[Number].longValue(), b.asInstanceOf[Number].longValue(), comparator)
      case ExpressionConstant.AsDateFormat => compareString(a.asInstanceOf[String], b.asInstanceOf[String], comparator)
      case ExpressionConstant.AsTimestampFromString => compareLong(a.asInstanceOf[Number].longValue(), b.asInstanceOf[Number].longValue(), comparator)
      case ExpressionConstant.AsPeriod => comparePeriod(a.asInstanceOf[Period], b.asInstanceOf[Period], comparator)
      case ExpressionConstant.AsJsonArray => throw new NotImplementedException
      case ExpressionConstant.AsJsonObject => throw new NotImplementedException
      case _ => throw new NoSuchElementException

    }
    // TODO: null conversions
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

  def getStringValue(x: Any): String = {
    x match {
      case (i: String) =>  i.replaceAll("\"", "")
    }
  }

  /**
    * Returns date in the given format
    * @param eventDate: Long (epochtime)
    * @param datePattern: String (pattern to format date) (If empty string is given then default is YYYYMMdd)
    * @return String (Date in given format)
    */
  def getEventDatePattern(eventDate: Long, datePattern: Option[String]): String = {
    //Here is how the code looks like with DateTimeFormatter
    val dateFormat = datePattern.getOrElse("YYYYMMdd")
    val dateTimeFormat = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneId.of("Etc/UTC"))
    val eventDateWithDateTimeFormat = dateTimeFormat.format(Instant.ofEpochMilli(eventDate))
    eventDateWithDateTimeFormat
  }

  /**
   * Returns date in the given format
   * @param eventDate: Long (epochtime)
   * @param datePattern: String (pattern to format date) (If empty string is given then default is YYYYMMdd)
   * @return String (Date in given format)
   */
  def getEventDatePatternFromString(eventDate: String, datePattern: Option[String]): Long = {
    //Here is how the code looks like with DateTimeFormatter
    val dateFormat = datePattern.getOrElse("yyyy-MM-dd")
    val dateTimeFormat = new SimpleDateFormat(dateFormat, Locale.getDefault)
    val eventDateWithDateTimeFormat = dateTimeFormat.parse(eventDate).getTime
    eventDateWithDateTimeFormat
  }


  def getCurrentDateTimeAsType(currentDateTime: Long, asType: String, format: Option[String]): Any = {
    // val currentDateTime = ZonedDateTime.now(ZoneId.of("Etc/UTC")).toEpochSecond
    val currentDateTimeAsType = asType match {
      case ExpressionConstant.AsLong => currentDateTime
      case ExpressionConstant.AsDateFormat => getEventDatePattern(currentDateTime, format)
      case ExpressionConstant.AsPeriod => Period.monthlyPeriodFromEpochDateTime(currentDateTime)
      // TODO: change from monthly period
    }
    //println(currentDateTimeAsType)
    currentDateTimeAsType
  }

  /**
    * Lookup util method to retrieve list of values from the table.
    * @param tableName: name of the table
    * @param lookUpBy: Map of key name and its value to lookup
    * @param selectField: Field in the table to be filtered
    * @return list of values filtered based on the selected field
    */
  def getExternalStatus(tableName: String,
                        lookUpBy: Map[String, String],
                        selectField: String,
                        lookupService: OxygenLookup) :List[String]  = {
    val valueList = lookupService.get(tableName,lookUpBy)
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

  /**
    * Returns type tag descriptor of a oxygen type
    * @param asType
    * @return
    */
  def getType(asType: String): TypeTag[_] = {
    val termType = asType match {
      case ExpressionConstant.AsString =>  typeTag[String]
      case ExpressionConstant.AsInt => typeTag[Int]
      case ExpressionConstant.AsBoolean => typeTag[Boolean]
      case ExpressionConstant.AsDouble => typeTag[Double]
      case ExpressionConstant.AsLong => typeTag[Long]
      case ExpressionConstant.AsPeriod => typeTag[Period]
      case ExpressionConstant.AsDateFormat => typeTag[Long]
      case ExpressionConstant.AsTimestampFromString => typeTag[Long]
      case ExpressionConstant.AsJsonArray => throw new NotImplementedException
      case ExpressionConstant.AsJsonObject => throw new NotImplementedException
    }
    termType
  }

  /**
    *
    * @param event
    * @param domain
    * @param elementName
    * @param elementType
    * @return
    */
  def getValueFromConsistencyParserEvent(event: ConsistencyParserEvents, domain: String, elementName: String, elementType: String, elementPattern: Option[String] ): Any = {

    val exprType = Util.getType(elementType).tpe
    type termType = exprType.type

    val elementValue = event match {
        case ParserSyntheticEvent(value) => (Util.getValueFromSyntheticEvent[termType] (value, domain, elementName, elementType), elementType)
        case ParserTaggedCuratedEvent(value) => (Util.getValueFromTaggedCuratedEvent[termType](value, domain, elementName, elementType), elementType)
        case _ =>  throw new NotImplementedException
    }

    if (elementType == ExpressionConstant.AsDateFormat) {
      val dateTermValue = Util.getEventDatePattern((elementValue._1).asInstanceOf[Long], elementPattern)
      dateTermValue
    }
    else
      elementValue._1

  }

  def doesTermExist(event: ConsistencyParserEvents, elementDomain: String, elementName: String): Boolean = {
    if (Util.isDomainGlobal(elementDomain)) {
      true
    } else {
      event match {
        case ParserSyntheticEvent(value) => {

          if(value.curatedRecordJson.containsKey(elementDomain)){
            val element = value.curatedRecordJson.get(elementDomain).asInstanceOf[Map[String,Any]]
            element.contains(elementName)
          }
          else value.curatedRecordJson.containsKey(elementName)
        }
        case ParserTaggedCuratedEvent(value) => {
          if(value.curatedRecordJson.containsKey(elementDomain)){
            val element = value.curatedRecordJson.get(elementDomain).asInstanceOf[Map[String,Any]]
            element.contains(elementName)
          }
          else value.curatedRecordJson.containsKey(elementName) || value.curatedRecordJson.containsKey(TaggedCuratedEvent.getCorrectedFieldName(elementName))
        }
        case _ =>  throw new NotImplementedException
      }
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
    if (minFrequency.isEqual( Frequency.none)) {
      Period.nonePeriod(elementValue)
    } else {
      minFrequency.unit  match {
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


  def getNumberOfAccountsInHC(hc: HeterogeneousCollection): Long = {
    val events = hc.taggedCuratedEvents
    val accountSet = new mutable.HashSet[Int]
    events.foreach( event => {
      val k = getSRKey(event.subscriber, event.subscriberRelation)
      accountSet.+=(k)
    })
    accountSet.size
  }

}
