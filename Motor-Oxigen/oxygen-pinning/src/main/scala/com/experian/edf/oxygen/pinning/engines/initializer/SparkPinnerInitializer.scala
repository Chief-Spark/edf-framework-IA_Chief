package  com.experian.edf.oxygen.pinning.engines.initializer

import com.experian.edf.oxygen.context.{AppContext, ApplicationContext}
import com.experian.edf.oxygen.domain.PinnedEventWithMap
import com.experian.edf.oxygen.domain.internal.{StampedIdentity, TaggedConsistencyInfo, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.domain.pinning.PinnedTaggedIdentity
import com.experian.edf.oxygen.pinning.engines.{MergedPin, PinnedEvent, Pinner, SplitPin, StampedEventId, TaggedIdentityWithEventId}

object SparkPinnerInitializer {

  def create(configLocations: Array[String], myParent: AppContext = null): Pinner = {
    val appContext = ApplicationContext.create(configLocations, myParent, Array(classOf[PinnedEventWithMap],
            classOf[PinnedEvent], classOf[PinnedTaggedIdentity], classOf[TaggedIdentityWithEventId], classOf[MergedPin],
            classOf[SplitPin], classOf[StampedEventId], classOf[StampedIdentity], classOf[TaggedCuratedEventWithMap],
            classOf[TaggedConsistencyInfo]))
    Pinner.create(appContext)
  }


}
