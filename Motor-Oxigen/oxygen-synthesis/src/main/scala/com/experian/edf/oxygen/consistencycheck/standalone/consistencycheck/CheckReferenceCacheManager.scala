package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import com.experian.edf.oxygen.consistencycheck.domain.ReferenceCache
import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.logging.performance.PerformanceLogger._
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.slf4j.LoggerFactory
import oxygen.stage.synthesis.consistencycheck.RefCacheManagerUtil

import java.util.{HashMap => JavaHashMap, Map => JavaMap}
import scala.collection.mutable.ListBuffer

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 8/5/20 at 12:57 PM
 */
class CheckReferenceCacheManager(referenceCacheConfigs: Map[String, ReferenceCache]) extends Serializable {
  private lazy val logger = LoggerFactory.getLogger(classOf[CheckReferenceCacheManager])
  val synthesisedCacheMap = new JavaHashMap[String, Int2ObjectOpenHashMap[ListBuffer[SyntheticEvent]]]()
  val partiallySynthesisedCacheMap = new JavaHashMap[String, Int2ObjectOpenHashMap[ListBuffer[SyntheticEvent]]]()
  referenceCacheConfigs.foreach(each => synthesisedCacheMap.put(each._1, new Int2ObjectOpenHashMap[ListBuffer[SyntheticEvent]]()))
  referenceCacheConfigs.foreach(each => partiallySynthesisedCacheMap.put(each._1, new Int2ObjectOpenHashMap[ListBuffer[SyntheticEvent]]()))


  def addToSynthesisedCache(subjectEvent: SyntheticEvent): Unit = addToCacheInternal(subjectEvent, synthesisedCacheMap)

  def addToPartiallySynthesisedCache(subjectEvent: SyntheticEvent): Unit = addToCacheInternal(subjectEvent, partiallySynthesisedCacheMap)

  private def addToCacheInternal(subjectEvent: SyntheticEvent, cache: JavaHashMap[String, Int2ObjectOpenHashMap[ListBuffer[SyntheticEvent]]]): Unit = {
    referenceCacheConfigs.foreach(cacheConfig => {
      val cacheKey = cacheConfig._2.predicates.foldLeft("") { (a, c) => getValue(c, subjectEvent) + a }.hashCode
      val predicateFromMap = cache.get(cacheConfig._1)
      if (predicateFromMap.containsKey(cacheKey))
        predicateFromMap.get(cacheKey).append(subjectEvent)
      else {
        predicateFromMap.put(cacheKey, ListBuffer(subjectEvent))
      }
    })
  }

  def removeFromCache(subjectEvent: List[SyntheticEvent]): Unit = RefCacheManagerUtil.removeFromCache(subjectEvent, synthesisedCacheMap)


  def retrieveFromCache(subjectEvent: TaggedCuratedEventWithMap): JavaMap[String, List[SyntheticEvent]] = {
    val retrievedCache: JavaMap[String, List[SyntheticEvent]] = new java.util.HashMap[String, List[SyntheticEvent]]()

    referenceCacheConfigs.foreach(cacheConfig => {
      val cacheKey: Int = cacheConfig._2.predicates.foldLeft("") { (a, c) => getValue(c, subjectEvent) + a }.hashCode
      val fullySynthesisedCache =synthesisedCacheMap.get(cacheConfig._1).getOrDefault(cacheKey, ListBuffer[SyntheticEvent]())
      val partialSynthesisedCache = partiallySynthesisedCacheMap.get(cacheConfig._1).getOrDefault(cacheKey, ListBuffer[SyntheticEvent]())
      val events = partialSynthesisedCache.toList ::: fullySynthesisedCache.toList
      retrievedCache.put(cacheConfig._1, events)
    })
    retrievedCache
  }

  def clearPartiallySyntheisedCache(): Unit = {
    partiallySynthesisedCacheMap.clear()
    referenceCacheConfigs.foreach(each => partiallySynthesisedCacheMap.put(each._1, new Int2ObjectOpenHashMap[ListBuffer[SyntheticEvent]]()))
  }


  def getValueForFieldAndCompare(fieldName: String, tce: TaggedCuratedEventWithMap, se: SyntheticEvent): Boolean = {
    try {
      getValue(fieldName, tce) == getValue(fieldName, se)
    } catch {
      case ex: Exception => false
    }
  }

  private def getValue(fieldName: String, se: SyntheticEvent): String = {
    try {
      fieldName match {
        case "subscriberRelation" => se.subscriberRelation
        case "asOfDate" => se.asOfPeriod.epochDateTime.toString
        case "dataSource" => se.dataSource
        case "subscriber" => se.subscriber
        case _ => se.curatedRecordJson.getOrDefault(fieldName, "").toString
      }
    } catch {
      case _: Exception => ""
    }
  }

  private def getValue(fieldName: String, tce: TaggedCuratedEventWithMap): String = {
    try {
      fieldName match {
        case "subscriberRelation" => tce.subscriberRelation
        case "asOfDate" => tce.asOfPeriod.epochDateTime.toString
        case "dataSource" => tce.lineage.dataSource
        case "subscriber" => tce.subscriber
        case _ => tce.curatedRecordJson.getOrDefault(fieldName, "").toString
      }
    } catch {
      case _: Exception => ""
    }
  }
}
