package com.experian.edf.oxygen.consistencycheck.app

import com.experian.edf.oxygen.consistencycheck.engines.initializer.SparkConsistencyCheckerInitializer

object SynthesisApp {

  def main(args: Array[String]) {

    val synthesizer = SparkConsistencyCheckerInitializer.create(args)
    //val synthesizer = SparkConsistencyCheckerInitializer.create(Array("env.conf", "consistency-application.conf", "oxygen-lookup.conf", "spark.conf"))
    synthesizer.run

  }
}


