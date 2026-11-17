package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import java.util.concurrent.ConcurrentHashMap

/**
 *
 * @author annapurna - C22603A
 *         Created on 3/21/20 at 5:43 PM
 */
case class SynthesisOptions(referenceEventWindowInMonths: Option[Int] = None,
                            fileTypeTimeMap: ConcurrentHashMap[String, Long] = new ConcurrentHashMap,
                            checkFileTagEnabled: Boolean = false,
                            dateTypePick: String = "",
                            consistencyCheckCategory: List[CheckCategoryElement] = List.empty,
                            stateCheckAttributes: StateCheckAttributes,
                            contextualOverrideAttributes: ContextualOverrideAttributes,
                            migrationSubscriberLoggingEnabled :Boolean = false ) extends Serializable

case class StateCheckAttributes(stateCheckEnabled: Boolean = false, stateName: String = "", stateId : String = "") extends  Serializable

case class ContextualOverrideAttributes(shouldFilterDeletedEvents: Boolean = false, shouldReportDeletedEvents: Boolean = false)

case class CheckCategoryElement(categoryName: String, categoryThreshold: Long)
