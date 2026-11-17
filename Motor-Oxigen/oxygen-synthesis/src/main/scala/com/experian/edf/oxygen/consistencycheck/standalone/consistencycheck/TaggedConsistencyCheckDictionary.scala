package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import com.experian.edf.oxygen.consistencycheck.standalone.parser.ast.{CheckASTNode, ReferenceASTNode, TriggerASTNode}
import com.experian.edf.oxygen.domain.ConsistencyInfo

/**
 *
 * @author annapurna - C22603A
 *         Created on 1/14/20 at 11:46 AM
 */
case class TaggedConsistencyCheckDictionary(checkName: String,
                                            checkProcessor: ConsistencyCheckProcessor = null.asInstanceOf[ConsistencyCheckProcessor],
                                            isNonPeriodic: Boolean = false,
                                            triggerCondition: Option[TriggerASTNode],
                                            withReferenceEvents: Option[ReferenceASTNode],
                                            check: Option[CheckASTNode],
                                            shouldFilterEventOnError: Boolean,
                                            shouldFilterFieldOnError: Boolean,
                                            filterFieldsOnError: List[String] = List.empty[String],
                                            consistencyInfo: ConsistencyInfo = null,
                                            useReferenceCache: String = null,
                                            checkCategory: String = null,
                                            checksWithInfo: List[GroupedChecks] = List.empty[GroupedChecks]
                                            ) extends Serializable {
}


case class GroupedChecks(check: Option[CheckASTNode],
                         checkProcessor: ConsistencyCheckProcessor = null.asInstanceOf[ConsistencyCheckProcessor],
                         shouldFilterEventOnError: Boolean,
                         shouldFilterFieldOnError: Boolean,
                         filterFieldsOnError: List[String] = List.empty[String],
                         consistencyInfo: ConsistencyInfo = null) extends Serializable {}

