package com.experian.edf.oxygen.curation.lookup

import com.experian.edf.oxygen.domain.processors.LookupProcessorContext
import com.experian.edf.oxygen.lookup.LookupProcessor

class ExampleLookupProcessor(ctx: LookupProcessorContext) extends LookupProcessor(ctx) {

  override def preProcessKey(tableName: String, keyName: String, keyVal: Any): Any = {

    logger.debug("table name is " + tableName + " indexedKey is " + keyName + " value is " + keyVal)

    val processedValue = if (tableName == "sampleTable2" && keyName == "nationalId") keyVal.toString.toUpperCase
    else if (tableName == "sampleTable2" && keyName == "firstName") removeAccent(keyVal.toString)
    else keyVal

    logger.debug("preprocessKey return value is " + processedValue)
    processedValue
  }

  def removeAccent(value: String) = {
    val processedValue = value.replace("ü", "u")
    logger.debug("replaced value is " + processedValue)
    processedValue
  }

}
