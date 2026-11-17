package com.experian.edf.oxygen.validation.app

import com.experian.edf.oxygen.validation.engines.initializer.SparkValidatorInitializer

object SparkValidationApp {

  def main(args: Array[String]) {

    val validation = SparkValidatorInitializer.create(args)
  //  val validation = SparkValidatorInitializer.create(Array("local/env-local.conf", "local/validation-application-local.conf",
  //    "local/spark-local.conf"))
    validation.run(true)
  }
}