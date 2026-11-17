package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedConsistencyInfo}

case class SynthesizedEventsWrapperWithCount(pin: Long,
                                    syntheticEvents: List[SyntheticEvent],
                                    consistencyInfo: List[TaggedConsistencyInfo],
                                    syntheticEventCount: Int,
                                    consistencyInfoCount: Int
                                   )

case class SynthesizedEventsWrapper(pin: Long,
                                    syntheticEvents: List[SyntheticEvent],
                                    consistencyInfo: List[TaggedConsistencyInfo]
                                   )