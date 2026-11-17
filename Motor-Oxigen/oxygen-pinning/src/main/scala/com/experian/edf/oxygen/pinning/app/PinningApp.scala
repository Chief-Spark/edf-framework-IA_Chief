package com.experian.edf.oxygen.pinning.app

import com.experian.edf.oxygen.pinning.engines.initializer.{SparkHeterogeneousCollectorInitializer, SparkPinnerInitializer}


object PinningApp {

  def main(args: Array[String]) {

    val pinner = SparkPinnerInitializer.create(args)
    pinner.run

    val hc = SparkHeterogeneousCollectorInitializer.create(args)
    hc.run

  }
}


