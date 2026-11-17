package com.experian.edf.oxygen.curation.engines.stategeneration

import java.sql.Timestamp
import com.experian.edf.oxygen.curation.domain.HFErrorEvent
import com.experian.edf.oxygen.domain.TaggedCuratedEventWithMap

class StateGenModels {}


private[oxygen] case class HFAggregateStateEventsInfo(aggregateStateEventsJsonStr: String,
                                                      errorEvent: HFErrorEvent)

private[oxygen] case class CuratedEventVO(event: TaggedCuratedEventWithMap,
                                          inputMsg: String,
                                          inputTopic: String,
                                          error: HFErrorEvent,
                                          timestamp: Timestamp = null,
                                          kPartition: Int = 0,
                                          validationStartTime: Long = 0L,
                                          validationEndTime: Long = 0L,
                                          curationStartTime: Long = 0L,
                                          curationEndTime: Long = 0L
                                         ) extends Serializable

private[oxygen] case class EventSet(value: String,
                                    pin: Long,
                                    reusedPin: Boolean,
                                    oxygenAttributesSet: String,
                                    timestamp: Timestamp,
                                    kPartition: Int = 0,
                                    eventId: String = "0",
                                    subscriber: String = "",
                                    messageType: String = "",
                                    validationStartTime: Long = 0L,
                                    validationEndTime: Long = 0L,
                                    curationStartTime: Long = 0L,
                                    curationEndTime: Long = 0L,
                                    stateGenStartTime: Long = 0L
                                   ) extends Serializable

case class MetricLatency(pin: Long,
                         applicationId: String,
                         reusedPin: Boolean,
                         subscriber: String,
                         eventId: String,
                         messageType: String,
                         validationStartTime: Long,
                         validationEndTime: Long,
                         curationStartTime: Long,
                         curationEndTime: Long,
                         stateGenStartTime: Long,
                         stateGenEndTime: Long)

