package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import com.experian.edf.oxygen.consistencycheck.domain.ReferenceCache
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 8/5/20 at 12:57 PM
 */
object ReferenceCacheOps extends Serializable {
  def createGlobalReferenceList(
                                 referenceCache: Map[String, ReferenceCache],
                                 subjectEvent: TaggedCuratedEventWithMap,
                                 syntheticEventList: List[SyntheticEvent],
                                 partiallySythesizedEvents: List[SyntheticEvent]
                               ): java.util.HashMap[String, (List[SyntheticEvent])] = {

    val referenceCacheMap = new java.util.HashMap[String, List[SyntheticEvent]]()
    referenceCache.foreach(eachCache => {
      val predicates = eachCache._2.predicates
      val partialEvents: List[SyntheticEvent] = partiallySythesizedEvents.filter(e => predicates.foldLeft(true) { (a, c) => getValueForFieldAndCompare(c, subjectEvent, e) && a })
      val fullEvents = syntheticEventList.filter(e => predicates.foldLeft(true) { (a, c) => getValueForFieldAndCompare(c, subjectEvent, e) && a })
      val referenceList = partialEvents ::: fullEvents
      referenceCacheMap.put(eachCache._1, referenceList)
    })

    referenceCacheMap
  }

  def getValueForFieldAndCompare(fieldName: String, tce: TaggedCuratedEventWithMap, se: SyntheticEvent): Boolean = {
    try {
      val tceFieldValue = fieldName match {
        case "subscriberRelation" => tce.subscriberRelation
        case "asOfDate" => tce.asOfPeriod
        case "dataSource" => tce.lineage.dataSource
        case "subscriber" => tce.subscriber
        case _ => tce.curatedRecordJson.get(fieldName)
      }
      val seFieldValue = fieldName match {
        case "subscriberRelation" => se.subscriberRelation
        case "asOfDate" => se.asOfPeriod
        case "dataSource" => se.dataSource
        case "subscriber" => se.subscriber
        case _ => se.curatedRecordJson.get(fieldName)
      }
      tceFieldValue == seFieldValue
    } catch {
      case ex: Exception => false
    }
  }
}
