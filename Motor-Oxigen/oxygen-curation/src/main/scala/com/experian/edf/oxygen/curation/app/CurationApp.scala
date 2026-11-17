package com.experian.edf.oxygen.curation.app

import com.experian.edf.oxygen.curation.engines.initializer.SparkCuratorInitializer


object CurationApp {

  def main(args: Array[String]) {

    val curator = SparkCuratorInitializer.create(args)
    //val curator = SparkCuratorInitializer.create(Array("local/env-local.conf", "local/curation-application-local.conf", "local/oxygen-lookup-aws.conf", "local/spark-local.conf"))
    curator.run

  }
}


