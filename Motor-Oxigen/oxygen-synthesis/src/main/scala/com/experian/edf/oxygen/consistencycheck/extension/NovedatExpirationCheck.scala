package com.experian.edf.oxygen.consistencycheck.extension

import com.experian.edf.oxygen.domain.{ConsistencyInfo, SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.domain.processors.ConsistencyCheckProcessorContext
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ConsistencyCheckProcessor
import com.experian.edf.oxygen.domain.internal.Period

/*
*
* Raises consistency error if the Account is expired
*
* */

class NovedatExpirationCheck(context: ConsistencyCheckProcessorContext)  extends ConsistencyCheckProcessor(context: ConsistencyCheckProcessorContext)  {

  val EMPTY = ""

  override def performCheck(event: TaggedCuratedEventWithMap, taggedCuratedEvents: List[SyntheticEvent]): ConsistencyInfo = {

    val currentPeriod: Period = event.getPeriod
    val isCreateAlert = if (event.curatedRecordJson.getOrDefault("isCreateAlert", EMPTY).toString.nonEmpty) true else false
    val isCreateComment = if (event.curatedRecordJson.getOrDefault("isCreateComment", EMPTY).toString.nonEmpty) true else false

    // Check to see if four or more years of data available
    if (taggedCuratedEvents.isEmpty)
      return null

    val expiredPeriod = if (isCreateAlert)
      taggedCuratedEvents.exists(x => ((x.asOfPeriod.epochDateTime <= currentPeriod.epochDateTime)
        && (x.curatedRecordJson.getOrDefault( "alertExpiryDate", EMPTY).toString.toLong > System.currentTimeMillis()
        || x.curatedRecordJson.getOrDefault("isDeleteAlert", EMPTY).toString.toBoolean)))
    else
      taggedCuratedEvents.exists(x => ((x.asOfPeriod.epochDateTime <= currentPeriod.epochDateTime)
        && (x.curatedRecordJson.getOrDefault( "commentExpiryDate", EMPTY).toString.toLong > System.currentTimeMillis()
        || x.curatedRecordJson.getOrDefault( "isDeleteComment", EMPTY).toString.toBoolean)))

    if (expiredPeriod && isCreateAlert)
      new ConsistencyError("", "", "1", "Duplicate create alert", currentPeriod)
    else if (expiredPeriod && isCreateComment)
      new ConsistencyError("", "", "1", "Duplicate create comment", currentPeriod)
    else
      null
  }

}
class ConsistencyError(code: String, subCode: String, level: String,
                       val detail: String,
                       val period: Period) extends ConsistencyInfo(code, subCode, level) {

}