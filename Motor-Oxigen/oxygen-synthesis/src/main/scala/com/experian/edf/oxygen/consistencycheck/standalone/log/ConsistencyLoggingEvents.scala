package com.experian.edf.oxygen.consistencycheck.standalone.log

import com.experian.edf.oxygen.domain.ConsistencyInfo
import com.experian.edf.oxygen.domain.internal.Lineage
import com.experian.edf.oxygen.utils.ScalaJacksonUtil

case class ConsistencyLogEvent(
                                pin: Long,
                                lineage: Lineage,
                                subscriberRelation: String,
                                eventLineage: Lineage,
                                checkName: String,
                                eventId: String,
                                consistencyInfo: ConsistencyInfo,
                                isFiltered: Boolean,
                                timestamp: String = "",
                                messageType: String
                              ) {
  override def toString = ScalaJacksonUtil.toJson(this).get
}
