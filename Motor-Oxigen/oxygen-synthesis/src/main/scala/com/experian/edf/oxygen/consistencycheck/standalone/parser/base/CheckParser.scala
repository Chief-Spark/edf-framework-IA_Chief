package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.{ExpressionConstant, StateCheck, Util}
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingField
import com.experian.edf.oxygen.domain.internal.Period
import org.slf4j.LoggerFactory.getLogger
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.collection.mutable


class CheckParser(thisEvent: TaggedCuratedEventWithMap, thatParserEvents: ConsistencyParserEvents, currentDateTime: Long, lookupService: OxygenLookup, relationshipMigrationTracker: mutable.HashMap[Int, RelationshipMigrationTrackingField],
                  stateCheck: StateCheck) extends BaseConsistencyParser with Serializable {

  @transient lazy val logger = getLogger(this.getClass)


  val thatEvents = thatParserEvents match {
    case ParserTaggedCuratedEventList(thatEvent) => thatEvent
    case ParserSyntheticEventList(thatEvent) => thatEvent
    case _ => throw new NotImplementedException
  }

  def or: Parser[Boolean => Boolean] = "||" ~ booleanExpression ^^ { case "||" ~ b => _ || b }

  def and: Parser[Boolean => Boolean] = "&&" ~ booleanExpression ^^ { case "&&" ~ b => _ && b }

  def applyCheck: Parser[Boolean] = booleanExpressionSequence | "(" ~> booleanExpressionSequence <~ ")"

  def booleanExpressionSequence: Parser[Boolean] = booleanExpression ~ rep(or | and) ^^ { case a ~ b => (a /: b) ((acc, f) => f(acc)) }

  def xorExpression: Parser[Boolean] = "xor" ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val domain = x._1._2._1
    val term = x._1._2._2
    val asType = x._1._2._3
    val exprType = Util.getType(asType).tpe
    type termType = exprType.type

    val xorValue = thatParserEvents match {
      case ParserTaggedCuratedEventList(thatEventsList) => thatEventsList.forall(Util.getValueFromTaggedCuratedEvent[termType](_, domain, term, asType) == Util.getValueFromTaggedCuratedEvent[termType](thatEventsList.head, domain, term, asType))
      case ParserSyntheticEventList(thatEventsList) => thatEventsList.forall(Util.getValueFromSyntheticEvent[termType](_, domain, term, asType) == Util.getValueFromSyntheticEvent[termType](thatEventsList.head, domain, term, asType))
      case _ => throw new NotImplementedException
    }
    xorValue
    // TODO: dateformat and period
  }

  def filterExpressionList: Parser[Map[String, String]] = repsep(filterExpression, ",") ^^ { x => x.reduce(_ ++ _) }

  def filterExpression: Parser[Map[String, String]] = stringVar ~ "==" ~ "this." ~ dictionaryTermWithType ^^ { x =>
    val tableFieldName = x._1._1._1
    val thisTermDomain = x._2._1
    val thisTermName = x._2._2
    val termType = x._2._3
    val thisEventValue = Util.getValueFromTaggedCuratedEvent[String](thisEvent, thisTermDomain, thisTermName, termType)
    Map(tableFieldName -> thisEventValue)
  } | stringVar ~ "==" ~ thatExpression ^^ { x =>
    val tableFieldName = x._1._1
    val thatEventValue = if (x._2 != null && x._2._1 != null) x._2._1.toString else ""
    Map(tableFieldName -> thatEventValue)
  } | stringVar ~ "==" ~ value ^^ { x =>
    val tableFieldName = x._1._1
    val thatEventValue = x._2.toString
    Map(tableFieldName -> thatEventValue)
  }

  def select: Parser[String] = ".select" ~ "(" ~ stringVar ~ ")" ^^ { x => x._1._2 }

  def externalExpression: Parser[(String, Map[String, String], String)] = "external." ~ stringVar ~ ".filter" ~ "(" ~ filterExpressionList ~ ")" ~ select ^^ { x =>
    val tableName = x._1._1._1._1._1._2
    val filterMap = x._1._1._2
    val checkFieldName = x._2
    (tableName, filterMap, checkFieldName)
  }

  def inExternalExpression: Parser[(String, String, Map[String, String], String)] = ("in" | "notIn") ~ externalExpression ^^ { x =>
    val cond = x._1
    val tableName = x._2._1
    val fieldMap = x._2._2
    val checkFieldName = x._2._3
    (cond, tableName, fieldMap, checkFieldName)
  }

  def inExpression: Parser[(String, String, String, String, Option[String])] = ("in" | "notIn") ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val in = x._1._1._1._1
    val termDomain = x._1._2._1
    val termName = x._1._2._2
    val termType = x._1._2._3
    val termPattern = x._1._2._4
    (in, termDomain, termName, termType, termPattern)
  }

  def inValueExpression: Parser[(String, List[Any])] = ("in" | "notIn") ~ "(" ~ valueSequence ~ ")" ^^ { x =>
    val in = x._1._1._1
    val values = x._1._2
    (in, values)
  }

  def currentDateTimeWithType: Parser[(Any, String)] = "env." ~ "CURRENT_DATE_TIME" ~ ".asDateFormat" ~ "(" ~ datePattern ~ ")" ^^ { x =>
    val asType = x._1._1._1._2.drop(1)
    val pattern = x._1._2
    (Util.getCurrentDateTimeAsType(currentDateTime, asType, Some(pattern)), asType)
  } | "env." ~ "CURRENT_DATE_TIME" ~ (".asPeriod" | ".asLong" | dateFormat) ^^ { x =>
    val asType = x._2.drop(1)
    val pattern = None
    (Util.getCurrentDateTimeAsType(currentDateTime, asType, pattern), asType)
  }

  def currentDateTimeWithOperator: Parser[(Any, String)] = currentDateTimeWithType ~ ("+" | "-") ~ value ^^ { x =>
    val currentValue = x._1._1._1
    val asType = x._1._1._2
    val op = x._1._2
    val thatValue = x._2
    (Util.applyOperation(op, asType, currentValue, thatValue), asType)

  }

  def currentDateTimeExpression: Parser[(Any, String)] = currentDateTimeWithOperator | currentDateTimeWithType

  // TODO: check opt, should return type as well

  def thatExpressionWithOperator: Parser[Any] = thatExpression ~ operator ~ value ^^ { x =>
    val thatTermValue = x._1._1._1
    val thatTermType = x._1._1._2
    val op = x._1._2
    val thatValue = x._2
    if (thatTermValue == null)
      null
    else
      Util.applyOperation(op, thatTermType, thatTermValue, thatValue)
  }

  def thatExpression: Parser[(Any, String)] = mostRecentExpression | firstExpression | lastExpression | anyOneExpression | prevPeriodExpression | distinctCountExpression | checkStateOpenExpression

  def booleanExpression: Parser[Boolean] = thatExpression ~ comparator ~ currentDateTimeExpression ^^ { x =>
    val thatValue = x._1._1._1
    val thatTermType = x._1._1._2
    val cond = x._1._2
    val value = x._2._1
    Util.compareValues(thatValue, value, cond, thatTermType)
  } | expression ~ comparator ~ currentDateTimeExpression ^^ { x =>
    val termDomain = x._1._1._2._1
    val termName = x._1._1._2._2
    val termType = x._1._1._2._3
    val termPattern = x._1._1._2._4
    val cond = x._1._2
    val rhsValue = x._2._1
    // TODO: error scenario
    val exprType = Util.getType(termType).tpe
    type termType = exprType.type

    val lhsTermValue = (Util.getValueFromTaggedCuratedEvent[termType](thisEvent, termDomain, termName, termType), termType)

    if (rhsValue == null) true
    else {
      if (termType == ExpressionConstant.AsDateFormat)
        Util.compareValues(Util.getEventDatePattern(lhsTermValue._1.asInstanceOf[Long], termPattern), rhsValue, cond, termType)
      else
        Util.compareValues(lhsTermValue._1, rhsValue, cond, termType)
    }
  } | xorExpression ~ comparator ~ value ^^ { x =>
    val lhsValue = x._1._1
    val cond = x._1._2
    val rhsValue = x._2.asInstanceOf[Boolean]
    Util.compareBoolean(lhsValue, rhsValue, cond)
  } | xorExpression | value ~ inExternalExpression ^^ { x =>
    val lhsValue = x._1
    val cond = x._2._1
    val tableName = x._2._2
    val fieldMap = x._2._3
    val checkFieldName = x._2._4
    val rhsList = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService)
    if (cond.toLowerCase() == "in")
      rhsList.contains(lhsValue)
    else
      !rhsList.contains(lhsValue)
  } | thatExpression ~ inExternalExpression ^^ { x =>
    val lhsValue = x._1._1.toString
    val cond = x._2._1
    val tableName = x._2._2
    val fieldMap = x._2._3
    val checkFieldName = x._2._4
    val rhsList = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService)
    if (cond.toLowerCase() == "in")
      rhsList.contains(lhsValue)
    else
      !rhsList.contains(lhsValue)
  } | expression ~ inExternalExpression ^^ { x =>
    val thisTermDomain = x._1._2._1
    val thisTermName = x._1._2._2
    val termType = x._1._2._3
    val lhsValue = Util.getValueFromTaggedCuratedEvent[String](thisEvent, thisTermDomain, thisTermName, termType)
    val cond = x._2._1
    val tableName = x._2._2
    val fieldMap = x._2._3
    val checkFieldName = x._2._4
    val rhsList = Util.getExternalStatus(tableName, fieldMap, checkFieldName, lookupService)
    if (cond.toLowerCase() == "in")
      rhsList.contains(lhsValue)
    else
      !rhsList.contains(lhsValue)
  } | expression ~ comparator ~ thatExpressionWithOperator ^^ { x =>
    val thisTermDomain = x._1._1._2._1
    val thisTermName = x._1._1._2._2
    val termType = x._1._1._2._3
    val termPattern = x._1._1._2._4
    val cond = x._1._2
    val thatValue = x._2

    val exprType = Util.getType(termType).tpe
    type termType = exprType.type

    val thisTermValue = (Util.getValueFromTaggedCuratedEvent[termType](thisEvent, thisTermDomain, thisTermName, termType), termType)

    if (thatValue == null) true
    else {
      if (termType == ExpressionConstant.AsDateFormat)
        Util.compareValues(Util.getEventDatePattern(thisTermValue._1.asInstanceOf[Long], termPattern), thatValue, cond, termType)
      else
        Util.compareValues(thisTermValue._1, thatValue, cond, termType)
    }

  } | expression ~ comparator ~ thatExpression ^^ { x =>
    val thisTermDomain = x._1._1._2._1
    val thisTermName = x._1._1._2._2
    val termType = x._1._1._2._3
    val termPattern = x._1._1._2._4
    val cond = x._1._2
    val thatValue = x._2._1

    val exprType = Util.getType(termType).tpe
    type termType = exprType.type

    val thisTermValue = (Util.getValueFromTaggedCuratedEvent[termType](thisEvent, thisTermDomain, thisTermName, termType), termType)
    // TODO: check this if
    if (thatValue == null) true
    else {
      if (termType == ExpressionConstant.AsDateFormat)
        Util.compareValues(Util.getEventDatePattern(thisTermValue._1.asInstanceOf[Long], termPattern), thatValue, cond, termType)
      else
        Util.compareValues(thisTermValue._1, thatValue, cond, termType)
    }
  } | expression ~ inExpression ^^ { x =>
    val thisTermDomain = x._1._2._1
    val thisTermName = x._1._2._2
    val termType = x._1._2._3
    val thisTermPattern = x._1._2._4
    val in = x._2._1
    val thatTermDomain = x._2._2
    val thatTermName = x._2._3
    val thatTermType = x._2._4
    val thatTermPattern = x._2._5

    val exprType = Util.getType(termType).tpe
    type thisTermType = exprType.type

    val thatExprType = Util.getType(thatTermType).tpe
    type thatTermType = thatExprType.type

    val found = {
      val thisTermValue = (Util.getValueFromTaggedCuratedEvent[thisTermType](thisEvent, thisTermDomain, thisTermName, termType), termType)
      thatParserEvents match {
        case ParserSyntheticEventList(thatEventsList) => if (thatEventsList.find(x => {
          val thatTermValue = (Util.getValueFromSyntheticEvent[thatTermType](x, thatTermDomain, thatTermName, thatTermType), thatTermType)
          thatTermValue._1 == thisTermValue._1
        }).orNull == null) false else true

        case ParserTaggedCuratedEventList(thatEventsList) => if (thatEventsList.find(x => {
          val thatTermValue = (Util.getValueFromTaggedCuratedEvent[thatTermType](x, thatTermDomain, thatTermName, thatTermType), thatTermType)
          thatTermValue == thisTermValue._1
        }).orNull == null) false else true

        case _ => throw new NotImplementedException

      }
    }

    if (in.toLowerCase == "in") found else !found

  } | "referenceEventCount" ~ comparator ~ floatingPointNumber ^^ { x =>
    val count = x._2.toInt
    val cond = x._1._2
    Util.compareInt(thatEvents.length, count, cond)
  } | "willBeMigrated" ~ comparator ~ booleanValue ^^ { x =>
    val boolValue = x._2
    val cond = x._1._2
    val trackingKey = Util.getSRKey(thisEvent.subscriber, thisEvent.subscriberRelation)
    val willBeMigrated = relationshipMigrationTracker.contains(trackingKey)
    Util.compareBoolean(willBeMigrated, boolValue, cond)
  } | thatExpression ~ inValueExpression ^^ { x =>
    val thatValue = x._1._1
    val thatTermType = x._1._2
    val inType = x._2._1
    val inValues = x._2._2

    if (inType == "in")
      inValues.contains(thatValue)
    else
      !inValues.contains(thatValue)
  } | thatExpression ~ comparator ~ value ^^ { x =>
    val thatValue = x._1._1._1
    val thatTermType = x._1._1._2
    val cond = x._1._2
    val value = x._2
    Util.compareValues(thatValue, value, cond, thatTermType)
  } | booleanValue | "(" ~> booleanExpression <~ ")" | "(" ~> booleanExpressionSequence <~ ")"

  def checkStateOpenExpression: Parser[(Any, String)] =
    SynthesisConstants.SHOULD_ACCEPT_STATE ~ "(" ~ SynthesisConstants.STATENAME ~ "==" ~ value ~ "," ~ SynthesisConstants.STATEID ~ "==" ~ value ~ "," ~ "this." ~ dictionaryTermWithType ~ ")" ^^ {
      x =>
        val fieldName = Util.getStringValue(x._1._1._1._1._1._1._1._1._2)
        val stateId = Util.getStringValue(x._1._1._1._1._2)
        val thisTermDomain = x._1._2._1
        val thisTermName = x._1._2._2
        val termType = x._1._2._3

        val thisEventValue = Util.getValueFromTaggedCuratedEvent[Long](thisEvent, thisTermDomain, thisTermName, termType)

        val stateCheckResult = try {
          val key = stateCheck.getSRSKey(fieldName, stateId, thisEvent.subscriber, thisEvent.subscriberRelation)
          val stateFieldList = stateCheck.getStateFieldsList(fieldName)
          //val stateField = stateCheck.getStateFields(fieldName)
          val result = stateCheck.isNewStateOpen(stateFieldList, fieldName, thisEventValue, key)
          result

        } catch {
          case e: Exception => {
            logger.error(s"Error on Checking {}, StateName {}, StateId {}, with a message of {} ", SynthesisConstants.SHOULD_ACCEPT_STATE, fieldName, stateId, e.getMessage)
            true
          }
        }
        (stateCheckResult, thisTermName)
    }

  def mostRecentExpression: Parser[(Any, String)] =
    "mostRecent" ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
      val thatTermDomain = x._1._2._1
      val thatTermName = x._1._2._2
      val thatTermType = x._1._2._3
      val thatTermPattern = x._1._2._4

      val thatExprType = Util.getType(thatTermType).tpe
      type thatTermType = thatExprType.type

      val ret = (getMostRecentValue[thatTermType](thatTermName, thatTermDomain, thatTermType, thatTermPattern), thatTermType)
      ret
      // (mostRecentValue, thatTermType)
    }


  def firstExpression: Parser[(Any, String)] = "first" ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val thatTermDomain = x._1._2._1
    val thatTermName = x._1._2._2
    val thatTermType = x._1._2._3
    val thatTermPattern = x._1._2._4

    val thatExprType = Util.getType(thatTermType).tpe
    type thatTermType = thatExprType.type

    val firstValue = if (thatEvents == null || thatEvents.isEmpty)
      (null, thatTermType)
    else {
      thatParserEvents match {
        case ParserSyntheticEventList(thatEventList) => (Util.getValueFromSyntheticEvent[thatTermType](thatEventList.head, thatTermDomain, thatTermName, thatTermType), thatTermType)
        case ParserTaggedCuratedEventList(thatEventList) => (Util.getValueFromTaggedCuratedEvent[thatTermType](thatEventList.head, thatTermDomain, thatTermName, thatTermType), thatTermType)
        case _ => throw new NotImplementedException
      }
    }
    if (thatTermType == ExpressionConstant.AsDateFormat)
      (Util.getEventDatePattern(firstValue._1.asInstanceOf[Long], thatTermPattern), thatTermType)
    else
      firstValue
  }

  def lastExpression: Parser[(Any, String)] = "last" ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val thatTermDomain = x._1._2._1
    val thatTermName = x._1._2._2
    val thatTermType = x._1._2._3
    val thatTermPattern = x._1._2._4

    val thatExprType = Util.getType(thatTermType).tpe
    type thatTermType = thatExprType.type

    val lastValue = if (thatEvents == null || thatEvents.isEmpty)
      (null, thatTermType)
    else {
      thatParserEvents match {
        case ParserSyntheticEventList(thatEventList) => (Util.getValueFromSyntheticEvent[thatTermType](thatEventList.last, thatTermDomain, thatTermName, thatTermType), thatTermType)
        case ParserTaggedCuratedEventList(thatEventList) => (Util.getValueFromTaggedCuratedEvent[thatTermType](thatEventList.last, thatTermDomain, thatTermName, thatTermType), thatTermType)
        case _ => throw new NotImplementedException
      }
    }
    if (thatTermType == ExpressionConstant.AsDateFormat)
      (Util.getEventDatePattern(lastValue._1.asInstanceOf[Long], thatTermPattern), thatTermType)
    else
      lastValue
  }

  def anyOneExpression: Parser[(Any, String)] = "anyOne" ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val thatTermDomain = x._1._2._1
    val thatTermName = x._1._2._2
    val thatTermType = x._1._2._3
    val thatTermPattern = x._1._2._4

    val thatExprType = Util.getType(thatTermType).tpe
    type thatTermType = thatExprType.type

    val lastValue = if (thatEvents == null || thatEvents.isEmpty)
      (null, thatTermType)
    else {
      thatParserEvents match {
        case ParserSyntheticEventList(thatEventList) => (Util.getValueFromSyntheticEvent[thatTermType](thatEventList.last, thatTermDomain, thatTermName, thatTermType), thatTermType)
        case ParserTaggedCuratedEventList(thatEventList) => (Util.getValueFromTaggedCuratedEvent[thatTermType](thatEventList.last, thatTermDomain, thatTermName, thatTermType), thatTermType)
        case _ => throw new NotImplementedException
      }
    }
    if (thatTermType == ExpressionConstant.AsDateFormat)
      (Util.getEventDatePattern(lastValue._1.asInstanceOf[Long], thatTermPattern), thatTermType)
    else
      lastValue
  }

  def prevPeriodExpression: Parser[(Any, String)] = "prevPeriod" ~ "(" ~ value ~ "," ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val periodCount = Util.getIntValue(x._1._1._1._1._2)
    val thatTermDomain = x._1._2._1
    val thatTermName = x._1._2._2
    val thatTermType = x._1._2._3
    val thatTermPattern = x._1._2._4

    assert(periodCount > 0)

    val thatEventList = thatEvents.asInstanceOf[List[SyntheticEvent]]

    if (thatEventList == null || thatEventList.isEmpty)
      (null, thatTermType)
    else {
      val currentPeriod = thisEvent.getPeriod
      val prevPeriod = currentPeriod.minus(periodCount)
      val prevPeriodThatEvents = thatEventList.filter(t => t.asOfPeriod == prevPeriod) // should only be one or zero event
      if (prevPeriodThatEvents == null || prevPeriodThatEvents.isEmpty)
        (null, thatTermType)
      else {
        val prevPeriodThatEvent = prevPeriodThatEvents.last
        val prevPeriodValue = thatTermType match {
          case ExpressionConstant.AsInt => Util.getValueFromSyntheticEvent[Int](prevPeriodThatEvent, thatTermDomain, thatTermName, thatTermType)
          case ExpressionConstant.AsString => Util.getValueFromSyntheticEvent[String](prevPeriodThatEvent, thatTermDomain, thatTermName, thatTermType)
          case ExpressionConstant.AsDouble => Util.getValueFromSyntheticEvent[Double](prevPeriodThatEvent, thatTermDomain, thatTermName, thatTermType)
          case ExpressionConstant.AsBoolean => Util.getValueFromSyntheticEvent[Boolean](prevPeriodThatEvent, thatTermDomain, thatTermName, thatTermType)
          case ExpressionConstant.AsLong => Util.getValueFromSyntheticEvent[Long](prevPeriodThatEvent, thatTermDomain, thatTermName, thatTermType)
          case ExpressionConstant.AsDateFormat => Util.getEventDatePattern(Util.getValueFromSyntheticEvent[Long](prevPeriodThatEvent, thatTermDomain, thatTermName, thatTermType), thatTermPattern)
          case _ => throw new NotImplementedException
        }
        (prevPeriodValue, thatTermType)
      }
    }
  }

  def distinctCountExpression: Parser[(Any, String)] = "distinctCount" ~ "(" ~ ("that." | "") ~ dictionaryTermWithType ~ ")" ^^ { x =>
    val thatTermDomain = x._1._2._1
    val thatTermName = x._1._2._2
    val thatTermType = x._1._2._3
    val thatTermPattern = x._1._2._4

    val thatExprType = Util.getType(thatTermType).tpe
    type thatTermType = thatExprType.type

    val distinctCountValue = if (thatEvents == null || thatEvents.isEmpty)
      (null, ExpressionConstant.AsInt)
    else {
      thatParserEvents match {
        case ParserSyntheticEventList(thatEventList) => thatEventList.map(thatEvent => (Util.getValueFromSyntheticEvent[thatTermType](thatEvent, thatTermDomain, thatTermName, thatTermType), thatTermType)).distinct.size
        case ParserTaggedCuratedEventList(thatEventList) => thatEventList.map(thatEvent => (Util.getValueFromTaggedCuratedEvent[thatTermType](thatEvent, thatTermDomain, thatTermName, thatTermType), thatTermType)).distinct.size
        case _ => throw new NotImplementedException
      }
    }

    (distinctCountValue, ExpressionConstant.AsInt)
  }

  // TODO: Replace this with the implementation in HCUtil
  def getMostRecentValue[T](thatTermName: String, thatTermDomain: String, thatTermType: String, thatTermPattern: Option[String]): T = {
    val sortedThatEvents = thatEvents.asInstanceOf[List[SyntheticEvent]].sortBy(_.asOfPeriod)(Ordering[Period].reverse)

    // TODO: remove Case - just return as T but check if Json can support that

    val thatExprType = Util.getType(thatTermType).tpe
    type thatTermType = thatExprType.type

    thatTermType match {
      case ExpressionConstant.AsInt => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            return Util.getValueForGlobalTermFromSyntheticEvent[T](e, thatTermName, thatTermType)
          } else if (e.curatedRecordJson.containsKey(thatTermName))
            return e.curatedRecordJson.get(thatTermName).asInstanceOf[T]
            else if(e.curatedRecordJson.containsKey(thatTermDomain))
            return e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName, null).asInstanceOf[T]

        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsString => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            return Util.getValueForGlobalTermFromSyntheticEvent[T](e, thatTermName, thatTermType)
          } else if (e.curatedRecordJson.containsKey(thatTermName))
            return e.curatedRecordJson.get(thatTermName).asInstanceOf[T]
            else if(e.curatedRecordJson.containsKey(thatTermDomain))
            return e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName, null).asInstanceOf[T]
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsDouble => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            return Util.getValueForGlobalTermFromSyntheticEvent[T](e, thatTermName, thatTermType)
          } else if (e.curatedRecordJson.containsKey(thatTermName))
            return e.curatedRecordJson.get(thatTermName).asInstanceOf[T]
            else if(e.curatedRecordJson.containsKey(thatTermDomain))
            return e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName, null).asInstanceOf[T]
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsBoolean => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            return Util.getValueForGlobalTermFromSyntheticEvent[T](e, thatTermName, thatTermType)
          } else if (e.curatedRecordJson.containsKey(thatTermName))
            return e.curatedRecordJson.get(thatTermName).asInstanceOf[T]
            else if(e.curatedRecordJson.containsKey(thatTermDomain))
            return e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName, null).asInstanceOf[T]
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsLong => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            return Util.getValueForGlobalTermFromSyntheticEvent[T](e, thatTermName, thatTermType)
          } else if (e.curatedRecordJson.containsKey(thatTermName))
            return e.curatedRecordJson.get(thatTermName).asInstanceOf[Number].longValue().asInstanceOf[T]
            else if(e.curatedRecordJson.containsKey(thatTermDomain))
            return e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName, null).asInstanceOf[T]
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsPeriod => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            return Util.getValueForGlobalTermFromSyntheticEvent[T](e, thatTermName, thatTermType)
          } else if (e.curatedRecordJson.containsKey(thatTermName))
            throw new NotImplementedException
            else if(e.curatedRecordJson.containsKey(thatTermDomain))
            throw new NotImplementedException
          null.asInstanceOf[T]
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsDateFormat => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            val mostRecentDate = Util.getValueForGlobalTermFromSyntheticEvent[Long](e, thatTermName, thatTermType)
            return Util.getEventDatePattern(mostRecentDate, thatTermPattern).asInstanceOf[T]
          } else if (e.curatedRecordJson.containsKey(thatTermName)) {
            val mostRecentDate = e.curatedRecordJson.get(thatTermName).asInstanceOf[Number].longValue()
            return Util.getEventDatePattern(mostRecentDate, thatTermPattern).asInstanceOf[T]
          }
            else if(e.curatedRecordJson.containsKey(thatTermDomain)) {
            val mostRecentDate = e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName,null).asInstanceOf[Number]
            if(mostRecentDate==null) return null.asInstanceOf[T]
            else return Util.getEventDatePattern(mostRecentDate.longValue(), thatTermPattern).asInstanceOf[T]
          }
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsTimestampFromString => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            val mostRecentDate = Util.getValueForGlobalTermFromSyntheticEvent[String](e, thatTermName, thatTermType)
            return Util.getEventDatePatternFromString(mostRecentDate, thatTermPattern).asInstanceOf[T]
          } else if (e.curatedRecordJson.containsKey(thatTermName)) {
            val mostRecentDate = e.curatedRecordJson.get(thatTermName).asInstanceOf[String]
            return Util.getEventDatePatternFromString(mostRecentDate, thatTermPattern).asInstanceOf[T]
          }
          else if(e.curatedRecordJson.containsKey(thatTermDomain)) {
            val mostRecentDate = e.curatedRecordJson.get(thatTermDomain).asInstanceOf[Map[String, Any]].getOrElse(thatTermName,null).asInstanceOf[String]
            if(mostRecentDate==null) return null.asInstanceOf[T]
            else return Util.getEventDatePatternFromString(mostRecentDate, thatTermPattern).asInstanceOf[T]
          }
        })
        null.asInstanceOf[T]
      }
      case ExpressionConstant.AsTimestampFromString => {
        sortedThatEvents.foreach(e => {
          if (Util.isDomainGlobal(thatTermDomain)) {
            val mostRecentDate = Util.getValueForGlobalTermFromSyntheticEvent[String](e, thatTermName, thatTermType)
            return Util.getEventDatePatternFromString(mostRecentDate, thatTermPattern).asInstanceOf[T]
          } else if (e.curatedRecordJson.containsKey(thatTermName)) {
            val mostRecentDate = e.curatedRecordJson.get(thatTermName).asInstanceOf[String]
            return Util.getEventDatePatternFromString(mostRecentDate, thatTermPattern).asInstanceOf[T]
          }
        })
        null.asInstanceOf[T]
      }
      case _ => throw new NotImplementedException
    }
  }
}

// TODO: code needs to be revisited
