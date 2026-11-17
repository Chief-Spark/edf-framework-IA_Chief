package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.domain.ConsistencyInfo

case class ConsistencyCheckDictionary(checkName: String,
                                      className: String = null,
                                      isNonPeriodic: Boolean = false,
                                      shouldSynthesizeSubjectEvent: Boolean = false,
                                      synthesisType: String = "withinDataSource",
                                      isEnabled: Boolean = true,
                                      triggerCondition: String,
                                      withReferenceEvents: String,
                                      check: String = null,
                                      shouldFilterEventOnError: String = null,
                                      shouldFilterFieldOnError: String = null,
                                      filterFieldsOnError: List[String] = List.empty[String],
                                      consistencyInfo: ConsistencyInfo = null,
                                      referenceCache: String = null,
                                      checkCategory: String = null,
                                      groupedChecks: List[CheckWithInfo] = List.empty[CheckWithInfo]
                                     ) extends Serializable {
}

object ConsistencyCheckDictionary extends Serializable {
  val CrossAccountCategory = "crossAccount"
}

case class CheckWithInfo(check: String = null,
                         className: String = null,
                         shouldFilterEventOnError: String = null,
                         shouldFilterFieldOnError: String = null,
                         filterFieldsOnError: List[String] = List.empty[String],
                         consistencyInfo: ConsistencyInfo = null) extends Serializable {}

case class ConsistencyCheckDictionaryWithReferenceCache(referenceCache: Array[ReferenceCache] = Array.empty[ReferenceCache], checks: Array[ConsistencyCheckDictionary] = Array.empty[ConsistencyCheckDictionary] ) extends Serializable {
}
case class ReferenceCache(name: String, predicates: List[String]) extends Serializable {
}

