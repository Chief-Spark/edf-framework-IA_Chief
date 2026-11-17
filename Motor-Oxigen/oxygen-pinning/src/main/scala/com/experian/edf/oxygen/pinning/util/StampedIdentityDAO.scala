package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.hbase.HbaseDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.domain.internal.StampedIdentity
import com.experian.edf.oxygen.hydrator.core.dehydrator.StampedIdentityDehydratorService
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.LoggerFactory
import org.slf4j.LoggerFactory.getLogger

class StampedIdentityDAO(
                          oxygenDataAccess: OxygenDataAccess,
                          stampedIdentityStateStore: String,
                          stampedIdentityRealTimeStore: String,
                          stampedIdentityDehydrator: StampedIdentityDehydratorService
                        ) extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def readStampedIdentity(identityId: Long): StampedIdentity = {

    // first read from real-time store
    val rtKeyValueEntry = new KeyValueEntry()
      .table(stampedIdentityRealTimeStore)
      .key(identityId.toString)
    val rtValue = oxygenDataAccess.getValueAsString(rtKeyValueEntry).orElse(null)
    val stampedIdentity =
      if (rtValue != null) {
        ScalaJacksonUtil.fromJson[StampedIdentity](rtValue).get
      }
      // if not found check in the batch store
      else {
        stampedIdentityDehydrator.dehydrate(identityId.toString)
      }
    stampedIdentity
  }

  private[oxygen] def stampedIdentityReadWithVersion(keyValueEntry: KeyValueEntry): ( String, Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry,true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version:Long = if (rtValueRaw != null) rtValueRaw._1 else -1L
    (rtValue, version)
  }

  private[oxygen] def stampedIdentityWriteWithVersionTry(keyValueEntry: KeyValueEntry,
                                                        withVersion:Boolean): Boolean = {
    val (oldKeyValueEntry,version) = stampedIdentityReadWithVersion(keyValueEntry)
    oxygenDataAccess.setValue(keyValueEntry, withVersion, version)
  }

  private[oxygen] def saveStampedIdentity(stampedIdentity: StampedIdentity) = {
    val keyValueEntry = new KeyValueEntry()
      .table(stampedIdentityRealTimeStore)
      .key(stampedIdentity.identityId.toString)
      .data(ScalaJacksonUtil.toJson(stampedIdentity).get)

    try {
      val retryInterval = 1 // millisecond
      val nTry = 10000
      var iTry = 0
      while ( {
        val isWritten = stampedIdentityWriteWithVersionTry(keyValueEntry, true)
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
