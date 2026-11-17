package com.experian.edf.oxygen.validation

import com.experian.edf.oxygen.validation.domain.ValidationProcessor

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ArrayBuffer

private[validation] class ContextTable(processor: ValidationProcessor, recs: Array[String]) {

  private val table2D = {
    val table2D = new ConcurrentHashMap[Int, ArrayBuffer[String]]

    for(newValue <- recs) {
      val keyHash = getKeyHash(processor.fileMetadata.dataSource)

      val existingValues = table2D.get(keyHash)
      if (existingValues != null)
          existingValues += newValue
      else {
          val newValues = new ArrayBuffer[String]()
          newValues += newValue
          table2D.put(keyHash, newValues)
      }
    }
    table2D
  }

  def get(key:String) : Option[Array[String]] = {
    val keyHash = getKeyHash(key)
    val values = table2D.get(keyHash)
    val retValues = if (values != null) {
      values.filter(v => {
        val savedKey = processor.fileMetadata.dataSource
        savedKey == key
      }).toArray
    } else {
      null
    }
    Some(retValues)
  }

  private[ContextTable] def getKeyHash(key: String) : Int = {
    key.hashCode
  }
}
