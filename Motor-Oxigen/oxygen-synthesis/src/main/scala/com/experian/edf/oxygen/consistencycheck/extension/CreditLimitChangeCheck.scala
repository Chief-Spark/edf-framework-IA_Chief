package com.experian.edf.oxygen.consistencycheck.extension

import com.experian.edf.oxygen.consistencycheck.extension.util.Util
import com.experian.edf.oxygen.domain.{ConsistencyInfo, SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.domain.processors.ConsistencyCheckProcessorContext
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ConsistencyCheckProcessor
import org.slf4j.LoggerFactory.getLogger

/**
  * Raises consistency warning if there is big change in the credit limit from the previous period
  */
class CreditLimitChangeCheck(context: ConsistencyCheckProcessorContext)  extends ConsistencyCheckProcessor(context: ConsistencyCheckProcessorContext)  {
  @transient lazy val logger = getLogger(this.getClass)

  /**
    *
    * @param event Event from the current period
    * @param syntheticEvents all the events for the subscriber relation
    * @return
    */
  override def performCheck(event: TaggedCuratedEventWithMap, syntheticEvents: List[SyntheticEvent]): ConsistencyInfo = {

    // get credit limit
    val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)

    // check if there credit limit attribute
    if (currCreditLimit >= 0.0) {
      val currentPeriod = event.getPeriod

      // get the previous period
      val prevPeriod = currentPeriod.previousPeriod

      // Get records from the previous period
      val previousPeriodEvents = syntheticEvents.filter(s => s.asOfPeriod == prevPeriod)

      // Get the credit limit
      val prevCreditLimit = getCreditLimit(previousPeriodEvents)
      if (prevCreditLimit >= 0.0) {

        // Check for the percentage change
        val incr = currCreditLimit/prevCreditLimit
        if (incr > 1.5) {
          val cInfo = new CreditLimitChangeInfo("100", "10", "2", "Massive increase in credit", currentPeriod)
          cInfo
        } else null
      } else
        null
    } else
      null
  }

  override def performChecks(event: TaggedCuratedEventWithMap, referenceEvents: List[SyntheticEvent]): (List[ConsistencyInfo], Boolean) = {

    val oxygenLookupServ = context.lookup
    val lookupRetValue = Util.getLookup("consistencyfiltertable1", Map("errorcode" -> "errorCode1"),"errorvalue", oxygenLookupServ )
    logger.error(s"*****************" + lookupRetValue.head)
    val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)
    val listCInfo : List[ConsistencyInfo] = List()
    if (currCreditLimit >= 10000.0) {
      val currentPeriod = event.getPeriod
      val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)
      val prevPeriod = currentPeriod.previousPeriod
      val previousPeriodEvents = referenceEvents.filter(t => t.asOfPeriod == prevPeriod)
      val prevCreditLimit = getCreditLimit(previousPeriodEvents)
      if (prevCreditLimit >= 0.0) {
        val cInfo = new CreditLimitChangeInfo("100", "11", "2", "Credit limit exsits", currentPeriod)
        val addedCInfo = cInfo +: listCInfo

        val incr = currCreditLimit/prevCreditLimit
        if (incr > 1.5) {
          val cInfo2 = new CreditLimitChangeInfo("100", "12", "2", "Massive increase in credit happened", currentPeriod)
          val addedCInfo2 = cInfo2 +: addedCInfo
          (addedCInfo2, true)
        } else (null, false)
      } else
        (null, false)
    } else
      (null, false)
  }


  private def getCreditLimit(previousPeriodEvents: List[SyntheticEvent]): Double = {
    previousPeriodEvents.foreach(e => {
      val creditLimit = getCreditLimitFieldValue(e.curatedRecordJson)
      if (creditLimit != null)
        return creditLimit
    })
    -1.0
  }

  private def getCreditLimitForNonPeriodic(previousPeriodEvents: List[TaggedCuratedEventWithMap]): Double = {
    previousPeriodEvents.foreach(e => {
      val creditLimit = getCreditLimitFieldValue(e.curatedRecordJson)
      if (creditLimit != null)
        return creditLimit
    })
    -1.0
  }

  private def getCreditLimitFieldValue(rec: java.util.Map[String, Any]): Double = {
    //val gson = new Gson()
    //val recJsonObj = gson.fromJson(rec, classOf[JsonObject])
    if (rec.containsKey("creditLimit"))
      rec.get("creditLimit").toString.toDouble
    else
      -1.0
  }

  override def performNonPeriodicCheck(event: TaggedCuratedEventWithMap, referenceEvents: List[TaggedCuratedEventWithMap]): ConsistencyInfo = {
    val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)
    if (currCreditLimit >= 0.0) {
      val currentPeriod = event.getPeriod
      val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)
      val prevPeriod = currentPeriod.previousPeriod
      val previousPeriodEvents = referenceEvents.filter(t => t.asOfPeriod == prevPeriod)
      val prevCreditLimit = 1.0
      if (prevCreditLimit >= 0.0) {
        val incr = currCreditLimit/prevCreditLimit
        if (incr > 1.5) {
          val cInfo = new CreditLimitChangeInfo("100", "10", "2", "Massive increase in credit", currentPeriod)
          cInfo
        } else null
      } else
        null
    } else
      null
  }

  override def performNonPeriodicChecks(event: TaggedCuratedEventWithMap, referenceEvents: List[TaggedCuratedEventWithMap]): (List[ConsistencyInfo], Boolean) = {
    val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)
    //    val listCInfo = new ListBuffer[ConsistencyInfo]
    val listCInfo : List[ConsistencyInfo] = List()
    if (currCreditLimit >= 10000.0) {
      val currentPeriod = event.getPeriod
      val currCreditLimit = getCreditLimitFieldValue(event.curatedRecordJson)
      val prevPeriod = currentPeriod.previousPeriod
      val previousPeriodEvents = referenceEvents.filter(t => t.asOfPeriod == prevPeriod)
      val prevCreditLimit = getCreditLimitForNonPeriodic(previousPeriodEvents)
      if (prevCreditLimit >= 0.0) {
        val cInfo = new CreditLimitChangeInfo("100", "11", "2", "Non Periodic Credit limit exsits", currentPeriod)
        val addedInfo = cInfo +: listCInfo
        val incr = currCreditLimit/prevCreditLimit
        if (incr > 1.5) {
          val cInfo2 = new CreditLimitChangeInfo("100", "12", "2", "Non Periodic Massive increase in credit", currentPeriod)
          val addedInfo2 = cInfo2 +: addedInfo
          (addedInfo2, true)
        } else (null, false)
      } else
        (null, false)
    } else
      (null, false)
  }


}




