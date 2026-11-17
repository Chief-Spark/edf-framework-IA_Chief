package com.experian.edf.oxygen.consistencycheck.standalone.custom

import com.experian.edf.oxygen.domain.internal.Period
import com.experian.edf.oxygen.domain.SyntheticEvent
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ExpressionConstant
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingField
import com.experian.edf.oxygen.consistencycheck.standalone.parser.base.{CheckParser, ParserSyntheticEventList}

import scala.collection.mutable

class CustomRuleCheckParser(syntheticEvents: List[SyntheticEvent],
                            currentDateTime: Long,
                            lookupService: OxygenLookup) extends CheckParser(null,
                                                                              ParserSyntheticEventList(syntheticEvents),
                                                                              currentDateTime, lookupService,
                                                                              mutable.HashMap.empty[Int, RelationshipMigrationTrackingField], null) {


  // asPeriod in duration is defaulted to monthly
  def durationValue: Parser[String] =  stringVar ~ (".asLong" | ".asPeriod") ^^ { x =>
    val value = x._1
    val asType = x._2.drop(1)
    val returnValue = if (asType == ExpressionConstant.AsLong)
      Period.monthlyPeriodFromEpochDateTime(value.toLong).id.toString
    else
      value.toInt.toString
    returnValue
  } | currentDateTimeExpression ^^ { x =>
    val asType = x._2
    val value = x._1
    val returnValue = if (asType == ExpressionConstant.AsLong)
      Period.monthlyPeriodFromEpochDateTime(value.asInstanceOf[Long]).id.toString
    else
      value.asInstanceOf[Period].id
    returnValue
  }

  // TODO: extend grammar to support other periods for duration

  // .asLong is the epoch timestamp, .asPeriod is Int Period.id which is of  form YYYYMM
  //    "duration": "(200108.asPeriod : env.CURRENT_DATE_TIME.asPeriod - 204)"
  def applyDurationCustomRule: Parser[(Long, Long)] =  "(" ~ "_" ~ ":" ~ durationValue ~ ")" ^^ { x =>
      val end = x._1._2
      (0L, end.toLong)
  } | "(" ~ durationValue ~ ":" ~ "_" ~ ")" ^^ { x =>
    val start = x._1._1._1._2
    (start.toLong, Long.MaxValue)
  } | "(" ~ durationValue ~ ":" ~ durationValue ~ ")" ^^ { x =>
    val start = x._1._1._1._2.toLong
    val end = x._1._2.toLong
    (start, end)
  }
}
