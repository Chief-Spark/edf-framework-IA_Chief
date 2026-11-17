package com.experian.edf.oxygen.validation.engines.initializer

import com.experian.edf.oxygen.context.{AppContext, ApplicationContext}
import com.experian.edf.oxygen.domain.internal.PinnedEvent
import com.experian.edf.oxygen.domain.{TaggedCuratedEvent, TaggedCuratedEventWithMap, TaggedValidatedEvent, TaggedValidatedEventWithMap}
import com.experian.edf.oxygen.validation.engines.Validator

object SparkValidatorInitializer {

  def create(configLocations: Array[String], myParent: AppContext = null): Validator = {
    val appContext = ApplicationContext.create(configLocations, myParent, Array(classOf[TaggedCuratedEvent],
      classOf[PinnedEvent], classOf[TaggedCuratedEventWithMap], classOf[TaggedValidatedEvent],
      classOf[TaggedValidatedEventWithMap], classOf[Map[String, Any]]))
    Validator.create(appContext)
  }


}
