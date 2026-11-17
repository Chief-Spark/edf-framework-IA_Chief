package com.experian.edf.oxygen.consistencycheck.engines.initializer

import com.experian.edf.oxygen.context.{AppContext, ApplicationContext}
import com.experian.edf.oxygen.consistencycheck.engines.ConsistencyChecker

object SparkConsistencyCheckerInitializer {

  def create(configLocations: Array[String], myParent: AppContext = null): ConsistencyChecker = {
    val appContext = ApplicationContext.create(configLocations, myParent, Array())
    ConsistencyChecker.create(appContext)
  }


}
