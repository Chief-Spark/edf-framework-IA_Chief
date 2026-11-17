package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory

import scala.collection.mutable.HashMap

class EUC(val domainDict: DomainDictionaryInternal, val HashFunction : String = "default" ) {
  @transient private lazy val logger = LoggerFactory.getLogger(classOf[EUC])

  private val allEUCDictFields = DomainDictionaryInternal.getAllEUCFields(domainDict)
  // TODO: eucFields should be made hash of hashtable  (s,sr -> (Fieldname -> value)) for efficiency
  private val eucFields = HashMap[Long, EUCEventField]()

  private val SREucFields = HashMap[Int, HashMap[String, EUCEventField]]()

  private def getnewSRKey(sub: String, relation: String): Int = {
    (sub + ":" + relation).hashCode
  }

  private def getKey(f: String, ds: String, sub: String, relation: String): Long = {
    CreateHashValue.getHash(f + ":" + ds + ":" + sub + ":" + relation, HashFunction )
  }

  private def getSRKey(f: String, sub: String, relation: String): Long = {
    CreateHashValue.getHash(f + ":" + sub + ":" + relation, HashFunction )
  }

  private[oxygen] def update(x: SyntheticEvent) = {
    if (x != null) {
      allEUCDictFields.foreach(f => {
        val existingValueInRecord = x.curatedRecordJson.get(f.name)
        val key = getSRKey(f.name, x.subscriber, x.subscriberRelation)
        if (existingValueInRecord != null) // && existingValueInRecord.!= Test.None)
        eucFields.put(key, EUCEventField(f.name, existingValueInRecord, x.asOfPeriod.epochDateTime, false))
      })
    }
  }

  private[oxygen] def update(x: TaggedCuratedEventWithMap) = {
    allEUCDictFields.foreach(f => {
      val correctedValue = if (x.curatedRecordJson.containsKey(f.name + "_corrected")) x.curatedRecordJson.get(f.name + "_corrected") else null
      val existingValueInRecord = if (x.curatedRecordJson.containsKey(f.name)) x.curatedRecordJson.get(f.name) else null
      if (f.synthesizer.isEmpty) {
        updateEUCField(f.name, correctedValue, x.asOfDate, x.subscriber, x.subscriberRelation, existingValueInRecord)
      }
      else {
        val key = getSRKey(f.name, x.subscriber, x.subscriberRelation)
        val cachedValueEUC = eucFields.get(key).orElse(None)
        val cachedValue = if (cachedValueEUC.isDefined) Some(cachedValueEUC.get.value) else None
        val synthesizer = f.synthesizer.get
        val customValueToBeUpdated = synthesizer.performFieldSynthesis(f.name, Option(correctedValue), cachedValue)
        updateEUCField(f.name, customValueToBeUpdated, x.asOfDate, x.subscriber, x.subscriberRelation, existingValueInRecord)
      }
    })
    // populate system EUC fields
    populateSystemEUCFields(x.asOfDate, x.subscriber, x.subscriberRelation)
  }

  private[oxygen] def updateEUCField(fieldToBeUpdated: String, valueToBeUpdated: Any, asOfDate: Long,
                                     subscriber: String, subscriberRelation: String, existingValueInRecord: Any) = {
    val key = getSRKey(fieldToBeUpdated, subscriber, subscriberRelation)
    val cachedValue = eucFields.get(key).orElse(null)
    if (valueToBeUpdated == null) {
      if (existingValueInRecord != null) {
        if (cachedValue == null)
          eucFields.put(key, EUCEventField(fieldToBeUpdated, existingValueInRecord, asOfDate, false))
        else {
          if (!cachedValue.get.corrected && cachedValue.get.asOfDate > asOfDate)
            eucFields.put(key, EUCEventField(fieldToBeUpdated, existingValueInRecord, asOfDate, false))
        }
      }
    } else {
      if (cachedValue == null)
        eucFields.put(key, EUCEventField(fieldToBeUpdated, valueToBeUpdated, asOfDate, true))
      else {
        if (!cachedValue.get.corrected || cachedValue.get.asOfDate < asOfDate)
          eucFields.put(key, EUCEventField(fieldToBeUpdated, valueToBeUpdated, asOfDate, true))
      }
    }
  }

  private[oxygen] def populateSystemEUCFields(asOfDate: Long, subscriber: String, subscriberRelation: String) = {
    val key = getSRKey("firstEventDate", subscriber, subscriberRelation)
    val cachedValue = eucFields.get(key).orElse(null)
    if (cachedValue == null) {
      val jp = new JsonParser()
      eucFields.put(key, EUCEventField("firstEventDate", asOfDate.toString, asOfDate, true))
    } else {
      if (cachedValue.get.asOfDate > asOfDate) {
        val jp = new JsonParser()
        eucFields.put(key, EUCEventField("firstEventDate", asOfDate.toString, asOfDate, true))
      }
    }
  }


  private[oxygen] def updateEUCAfterMigration(transformedFields: Option[Array[String]], event: SyntheticEvent) = {
    transformedFields match {
      case None => {
        allEUCDictFields.foreach(f => {
          val existingValueInRecord = event.curatedRecordJson.get(f.name)
          updateEUCField(f.name, existingValueInRecord, event.asOfPeriod.epochDateTime, event.subscriber, event.subscriberRelation, existingValueInRecord)
        })
      }
      case Some(fields) => {
        val eucFieldsNames = allEUCDictFields.map(_.name)
        fields.foreach(name => if (eucFieldsNames.contains(name)) {
          val existingValueInRecord = event.curatedRecordJson.get(name)
          updateEUCField(name, existingValueInRecord, event.asOfPeriod.epochDateTime, event.subscriber, event.subscriberRelation, existingValueInRecord)
        })
      }
    }
    // populate system EUC fields
    populateSystemEUCFields(event.asOfPeriod.epochDateTime, event.subscriber, event.subscriberRelation)
  }

  /**
   * Returns EUC.
   *
   * EUC for a field is the earliest value of the field unless it was corrected (by a explicit correction event).  In case
   * there was a correction, it returns the latest corrected value.
   *
   * @param fieldName
   * @param subscriber
   * @param subscriberRelation
   * @return
   */
  private[oxygen] def get(fieldName: String, dataSource: String, subscriber: String, subscriberRelation: String): Option[EUCEventField] = {
    //val key = getKey(fieldName, dataSource, subscriber, subscriberRelation)
    val key = getSRKey(fieldName, subscriber, subscriberRelation)
    eucFields.get(key)
  }

  private[oxygen] def getFirstEventDate(dataSource: String, subscriber: String, subscriberRelation: String): Long = {
    val maybeField = get("firstEventDate", dataSource, subscriber, subscriberRelation)
    if (maybeField.isDefined && maybeField.get.value != None)
      ScalaJacksonUtil.fromJson[Long](maybeField.get.value.toString).get
    else
      null.asInstanceOf[Long]
  }

  def propagate(events: List[SyntheticEvent]): Unit = {
    val eucFieldNames: List[String] = domainDict.fields.filter(_.handler.equalsIgnoreCase("euc")).map(_.name).toList
    if (logger.isDebugEnabled) {
      logger.debug("Number of EUC fields : {}", eucFields.size)
      logger.debug("Number of Events : {}", events.size)
      logger.debug("EUC Fields {} : {}", eucFieldNames.size, eucFieldNames.mkString(", "))
      logger.debug("Running with Parallel Streams of Events")
    }
    events.par.foreach(se => {
      eucFieldNames.foreach(fieldName => {
        val key = getSRKey(fieldName, se.subscriber, se.subscriberRelation)
        val eucValue = eucFields.get(key).orNull
        if (eucValue != null) {
          se.curatedRecordJson.put(fieldName, eucValue.value)
        }
      })
    })
  }
}
