package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable.HashMap

class LAP(domainDict:DomainDictionaryInternal, val HashFunction : String = "default" ) {
  private val allLAPDictFields = DomainDictionaryInternal.getAllLAPFields(domainDict)
  private val lapFields = HashMap[Long, LAPEventField]()
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def update(x: SyntheticEvent) = {
    if (x != null) {
      allLAPDictFields.foreach(f => {
        val value = x.curatedRecordJson.get(f.name)
        updateLAPFieldValue(f.name, value, x.asOfPeriod.epochDateTime, x.subscriber, x.subscriberRelation)
      })
    }
  }

  private[oxygen] def update(x: TaggedCuratedEventWithMap) = {
    allLAPDictFields.foreach(f => {
      val value = if (x.curatedRecordJson.containsKey(f.name)) x.curatedRecordJson.get(f.name) else null
      if (f.synthesizer.isEmpty) {
        updateLAPFieldValue(f.name, value, x.asOfDate, x.subscriber, x.subscriberRelation)
      }
      else
        {
          val key = getSRKey(f.name, x.subscriber, x.subscriberRelation)
          val cachedValueLAP = lapFields.get(key).orElse(None)
          val cachedValue =  if (cachedValueLAP.isDefined) Some(cachedValueLAP.get.value) else None
          val synthesizer = f.synthesizer.get
          val customValueToBeUpdated = synthesizer.performFieldSynthesis(f.name, Option(value), cachedValue)
          updateLAPFieldValue(f.name, customValueToBeUpdated, x.asOfDate, x.subscriber, x.subscriberRelation)
        }
    })
  }

  private[oxygen] def updateLAPFieldValue(fieldToBeUpdated: String, valueToBeUpdated: Any, asOfDate: Long,
                                          subscriber: String, subscriberRelation: String) = {
      val key = getSRKey(fieldToBeUpdated, subscriber, subscriberRelation)
      val cachedValue = lapFields.get(key).orElse(null)
      if (valueToBeUpdated != null) {
        if (cachedValue == null)
          lapFields.put(key, LAPEventField(fieldToBeUpdated, valueToBeUpdated, asOfDate))
        else {
          if (cachedValue.get.asOfDate <= asOfDate)
            lapFields.put(key, LAPEventField(fieldToBeUpdated, valueToBeUpdated, asOfDate))
        }
      }
  }

  private[oxygen] def  updateLAPAfterMigration(transformedFields: Option[Array[String]], event: SyntheticEvent) = {
    transformedFields match {
      case None => {
        allLAPDictFields.foreach(f => {
          val valueInRecord = event.curatedRecordJson.get(f.name)
          updateLAPFieldValue(f.name, valueInRecord, event.asOfPeriod.epochDateTime, event.subscriber, event.subscriberRelation)
        })
      }
      case Some(fields) => {
        val eucFieldsNames = allLAPDictFields.map(_.name)
        fields.foreach(name => if(eucFieldsNames.contains(name))
        {
          val valueInRecord = event.curatedRecordJson.get(name)
          updateLAPFieldValue(name, valueInRecord, event.asOfPeriod.epochDateTime, event.subscriber, event.subscriberRelation)
        })
      }
    }
  }

  private def getKey(f:String, ds: String, sub: String, relation: String): Long = {
    //HashFunctions.getHash(f + ":" + ds + ":" + sub + ":" + relation)
    CreateHashValue.getHash(f + ":" + ds + ":" + sub + ":" + relation,HashFunction)
  }


  private def getSRKey(f: String, sub: String, relation: String): Long = {
   // HashFunctions.getHash(f + ":" + sub + ":" + relation)
    CreateHashValue.getHash(f + ":" + sub + ":" + relation,HashFunction)
  }

  /**
    *
    * @param fieldName
    * @param subscriber
    * @param subscriberRelation
    * @return
    */
  private[oxygen] def get(fieldName: String, dataSource: String, subscriber: String, subscriberRelation: String): Option[LAPEventField] = {
    //val key = getKey(fieldName, dataSource, subscriber, subscriberRelation)
    val key = getSRKey(fieldName, subscriber, subscriberRelation)
    lapFields.get(key)
  }
}


object CreateHashValue extends Serializable {

  def getHash(valueToHash:String, hashFunction:String ) : Long = {
   hashFunction match {
    case "SHA1" => HashFunctions.getHash(valueToHash)
    case _ =>  valueToHash.hashCode
  }
}

}

/**
 * This was moved to avoid add the entire dependencie
 */
object HashFunctions extends Serializable {

  /**
   * returns 40 bytes SHA-1 encoding.
   * @param s
   * @return
   */
  @throws(classOf[OxygenException])
  def getSHA1Hash(s: String): String = {

    Option(s) match {
      case None => throw new OxygenException("String to hash cannot be null or empty")
      case default => {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
      }
    }
  }

  // TODO: Consider changing this the murmur hash
  // e.g. Hashing.murmur3_128(179426549).hashBytes(s.getBytes).asLong
  def getHash(s: String): Long = {
    val sha1 = getSHA1Hash(s)
    var h = 1125899906842597L; // prime
    sha1.foreach(c => h = 31 * h + c)
    h
  }
}
/*
class LAP2(events: Array[TaggedCuratedEvent], domainDict:DomainDictionary) {

  private val allLAPDictFields = DomainDictionary.getAllLAPFields(domainDict)
  private val lapFields = HashMap[Int, LAPEventField]()

  events.foreach(e => {
    populateLAPFields(e)
  })

  private def populateLAPFields(x: TaggedCuratedEvent) = {
    allLAPDictFields.foreach(f => {
      val key = getKey(f.name, x.lineage.dataSource,  x.lineage.subscriber, x.subscriberRelation)
      val cachedValue = lapFields.get(key).orElse(null)
      val value = x.curatedRecordJson.get(f.name)
      if (value != null) {
        if (cachedValue == null)
          lapFields.put(key, LAPEventField(f.name, value, x.eventId, x.asOfDate))
        else {
          if (cachedValue.get.asOfDate < x.asOfDate)
            lapFields.put(key, LAPEventField(f.name, value, x.eventId, x.asOfDate))
        }
      }
    })
  }

  private def getKey(f:String, ds: String, sub: String, relation: String): Int = {
    (f + ":" + ds + ":" + sub + ":" + relation).hashCode
  }

  /**
    *
    * @param fieldName
    * @param dataSource
    * @param subscriber
    * @param subscriberRelation
    * @return
    */
  private[oxygen] def get(fieldName: String, dataSource: String, subscriber: String, subscriberRelation: String): Option[LAPEventField] = {
    val key = getKey(fieldName, dataSource, subscriber, subscriberRelation)
    lapFields.get(key)
  }

} */
