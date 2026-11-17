package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.pinning.domain.PinnedTaggedIdentity
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable.ListBuffer

class IdentityPinMapDAO(
  oxygenDataAccess: OxygenDataAccess,
  identityPinMapRealTimeStore: String
) extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def readPinnedTaggedIdentityMap(): ListBuffer[PinnedTaggedIdentity] = {
    val keyValueEntry = new KeyValueEntry()
      .table(identityPinMapRealTimeStore)

    val allValue = oxygenDataAccess.getAllValues(keyValueEntry)
    val pinnedTaggedIdentityMap = convertToPinnedTaggedIdentity(allValue)

    pinnedTaggedIdentityMap
  }

  private[oxygen] def identityPinMapReadWithVersion(keyValueEntry: KeyValueEntry): ( String, Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry,true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version:Long = if (rtValueRaw != null) rtValueRaw._1 else -1L
    (rtValue, version)
  }

  private[oxygen] def identityPinMapWriteWithVersionTry(keyValueEntry: KeyValueEntry,
                                  withVersion:Boolean): Boolean = {
    val (oldKeyValueEntry,version) = identityPinMapReadWithVersion(keyValueEntry)
    oxygenDataAccess.setValue(keyValueEntry, withVersion, version)
  }

  private[oxygen] def saveIdentityPinMap(pinnedTaggedIdentity: PinnedTaggedIdentity) = {
    val keyValueEntry = new KeyValueEntry()
      .table(identityPinMapRealTimeStore)
      .key(pinnedTaggedIdentity.pin.toString)
      .data(ScalaJacksonUtil.toJson(pinnedTaggedIdentity).get)

    try {
      val retryInterval = 1 // millisecond
      val nTry = 10000
      var iTry = 0
      while ( {
        val isWritten = identityPinMapWriteWithVersionTry(keyValueEntry, true)
        iTry += 1
        if (iTry>nTry) {
          logger.error("Error: HBase concurrent write timeout - saveIdentityPinMap")
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

  private[oxygen] def convertToPinnedTaggedIdentity(listBuffer: ListBuffer[String]): ListBuffer[PinnedTaggedIdentity] = {
    val pinnedTaggedIdentityMap = listBuffer
      .map(each =>
        ScalaJacksonUtil.fromJson(each, classOf[PinnedTaggedIdentity]).get)

    pinnedTaggedIdentityMap
  }
}
