package com.experian.edf.oxygen.acceptance.engines.initializer

import com.experian.edf.oxygen.acceptance.engines.Acceptor
import com.experian.edf.oxygen.configuration.configs.AcceptanceElement
import com.experian.edf.oxygen.context.{AppContext, ApplicationContext}
import com.experian.edf.oxygen.domain.internal.{TaggedCuratedEvent, TaggedCuratedEventWithMap}



object SparkAcceptanceInitializer {

  def create(configLocations: Array[String], myParent: AppContext = null): Acceptor = {
    val appContext = ApplicationContext.create(configLocations, myParent, Array(
      classOf[TaggedCuratedEventWithMap],
      classOf[TaggedCuratedEvent]
    ))
    Acceptor.create(appContext)
  }

}
