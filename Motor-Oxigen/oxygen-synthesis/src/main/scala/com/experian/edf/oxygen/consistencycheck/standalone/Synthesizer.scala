package com.experian.edf.oxygen.consistencycheck.standalone

import com.experian.edf.oxygen.consistencycheck.standalone.custom.CustomRuleChecker
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.ConsistencyCheckOps
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationTrackingLogging
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.SynthesisOptions
import com.experian.edf.oxygen.consistencycheck.standalone.migration.RelationshipMigrationProcessor
import com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck.TaggedConsistencyCheckDictionary
import com.experian.edf.oxygen.consistencycheck.domain._
import com.experian.edf.oxygen.domain.{ConsistencyInfo, SyntheticEvent, TaggedConsistencyInfo}
import com.experian.edf.oxygen.lookup.OxygenLookup
import org.slf4j.LoggerFactory.getLogger
/**
 * Synthesizer
 *
 * @author c22603a - Annadatha, Annapurna
 *
 **/
class Synthesizer(domainDictionary: DomainDictionaryInternal,
                  checks: Array[TaggedConsistencyCheckDictionary],
                  referenceCache: Map[String, ReferenceCache],
                  oxygenLookup: OxygenLookup,
                  currentDateTime: Long,
                  migrationProcessor: RelationshipMigrationProcessor = null.asInstanceOf[RelationshipMigrationProcessor],
                  customRule: Array[CustomRule] = Array.empty,
                  synthesisOptions: SynthesisOptions,
                  fieldHandlerHashFunction: String
                 ) extends Serializable {

  @transient lazy val logger = getLogger(this.getClass)

  /**
   *
   * @param heterogeneousCollection
   * @return
   */
  def synthesize(heterogeneousCollection: HeterogeneousCollection): (SynthesizedEventsWrapperWithCount,List[RelationshipMigrationTrackingLogging]) = {
    val res = ConsistencyCheckOps.performChecks(checks, heterogeneousCollection, domainDictionary, currentDateTime, oxygenLookup, migrationProcessor, referenceCache, synthesisOptions,fieldHandlerHashFunction)
    logger.debug(s"--| Count of inconsistencies: {}", res._1.length.toString)
    logger.debug(s"--| Applying Custom rules")
    CustomRuleChecker.applyCustomRule(res._2, customRule, currentDateTime, oxygenLookup)
    (SynthesizedEventsWrapperWithCount(heterogeneousCollection.pin, res._2, res._1, res._2.size, res._1.size),res._3)
  }

  /**
   *
   * @param heterogeneousCollection
   * @return
   */
  def defaultSynthesizer(heterogeneousCollection: HeterogeneousCollection): SynthesizedEventsWrapper = {
    val syntheticEvents: Array[SyntheticEvent] = heterogeneousCollection.taggedCuratedEvents.map(each => SyntheticEvent(
      dataSource = each.lineage.dataSource,
      subscriber = each.lineage.subscriber,
      subscriberRelation = each.subscriberRelation,
      frequency = each.minFrequency,
      asOfPeriod = each.asOfPeriod,
      parentEventIds = Array(each.eventId),
      curatedRecordJson = each.curatedRecordJson,
      migrationHistory = Array.empty

    ))
    val consistencyInfo = List(TaggedConsistencyInfo(pin = heterogeneousCollection.pin,
      lineage = heterogeneousCollection.taggedCuratedEvents.head.lineage,
      subscriberRelation = "someRelation",
      eventLineage = heterogeneousCollection.taggedCuratedEvents.head.lineage,
      checkName = "someName",
      eventId = System.currentTimeMillis().toString,
      consistencyInfo = new ConsistencyInfo("code", "subcode", "1"),
      isFiltered = false,
      timestamp = System.currentTimeMillis().toString))
    SynthesizedEventsWrapper(pin = heterogeneousCollection.pin, syntheticEvents.toList, consistencyInfo)

  }

}
