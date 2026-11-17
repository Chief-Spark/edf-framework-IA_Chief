package com.experian.edf.oxygen.acceptance.app

import com.experian.edf.oxygen.acceptance.engines.initializer.SparkAcceptanceInitializer

object AcceptanceApp {

  def main(args: Array[String]) {

     val acceptor = SparkAcceptanceInitializer.create(args)
    // val acceptor = SparkAcceptanceInitializer.create(Array("env.conf", "acceptance-application.conf","spark.conf"))
    acceptor.run()

  }
}

