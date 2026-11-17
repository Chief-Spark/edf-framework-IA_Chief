package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.domain.pinning.IndexPins
import com.experian.edf.oxygen.hydrator.core.dehydrator.PinIndexDehydratorService
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.LoggerFactory.getLogger

class PinIndexDAO(
                   oxygenDataAccess: OxygenDataAccess,
                   pinIndexRealTimeStore: String,
                   pinIndexDehydrator: PinIndexDehydratorService
                 ) extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def readPinIndex(indexName: String): List[String] = {

    // first read from real-time store
    val rtKeyValueEntry = new KeyValueEntry()
      .table(pinIndexRealTimeStore)
      .key(indexName)
    val rtValue = oxygenDataAccess.getValueAsString(rtKeyValueEntry).orElse(null)
    val pinIndex =
      if (rtValue != null) {
//        ScalaJacksonUtil.fromJson[Array[String]](rtValue).get
        ScalaJacksonUtil.fromJson[IndexPins](rtValue).get.pins
      }
      // if not found check in the batch store
      else {
        pinIndexDehydrator.dehydrate(indexName)
      }
    pinIndex
  }

  private[oxygen] def pinIndexReadWithVersion(keyValueEntry: KeyValueEntry): ( String, Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry,true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version:Long = if (rtValueRaw != null) rtValueRaw._1 else -1L
    (rtValue, version)
  }

  private[oxygen] def pinIndexWriteWithVersionTry(keyValueEntry: KeyValueEntry,
                                                        withVersion:Boolean): Boolean = {
    val (oldKeyValueEntry,version) = pinIndexReadWithVersion(keyValueEntry)
    oxygenDataAccess.setValue(keyValueEntry, withVersion, version)
  }

  private[oxygen] def savePinIndex(pinIndex: IndexPins) = {
    val keyValueEntry = new KeyValueEntry()
      .table(pinIndexRealTimeStore)
      .key(pinIndex.index)
      .data(ScalaJacksonUtil.toJson(pinIndex).get)

    try {
      val retryInterval = 1 // millisecond
      val nTry = 10000
      var iTry = 0
      while ( {
        val isWritten = pinIndexWriteWithVersionTry(keyValueEntry, true)
        iTry += 1
        if (iTry>nTry) {
          logger.error("Error: HBase concurrent write timeout - saveStampedIdentity")
        }
        (!isWritten) && (iTry<=nTry)
      }) {Thread.sleep(retryInterval)}
    } catch {
      case e: Exception =>
      {
        oxygenDataAccess.setValue(keyValueEntry)
      }
    }
  }

}
