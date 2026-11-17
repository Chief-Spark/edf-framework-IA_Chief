package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.slf4j.LoggerFactory.getLogger

class NearNeighborDAO(
                       oxygenDataAccess: OxygenDataAccess,
                       nearNeighborStateStore: String,
                       nearNeighborRealTimeStore: String,
                       nearNeighborsDehydrator: NearNeighborsDehydratorService
                     ) extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  private def readNearNeighborFromRealTimeStore(key: String): Array[TaggedIdentity] = {
    val keyValueEntry = new KeyValueEntry()
      .table(nearNeighborRealTimeStore)
      .key(key)

    val value = oxygenDataAccess.getValueAsString(keyValueEntry).orElse(null)
    val taggedIdentities = getTaggedIdentityArrayFromJSONString(value)
    taggedIdentities
  }

  def nearNeighborReadWithVersion(keyValueEntry: KeyValueEntry): ( Array[TaggedIdentity], Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry,true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version:Long = if (rtValueRaw != null) rtValueRaw._1 else -1L
    val taggedIdentities = getTaggedIdentityArrayFromJSONString(rtValue)
    (taggedIdentities, version)
  }

  def nearNeighborWriteWithVersionTry(ti: TaggedIdentity, key: String, keyValueEntry: KeyValueEntry,
                                               withVersion:Boolean): Boolean = {
    val (existingNearNeighbors,version) = nearNeighborReadWithVersion(keyValueEntry)

    val valueToSave = if (existingNearNeighbors.isEmpty) {
      ScalaJacksonUtil.toJson(Array(ti)).get
    } else {
      ScalaJacksonUtil.toJson(ti +: existingNearNeighbors).get
    }
    val newKeyValueEntry = new KeyValueEntry()
      .table(nearNeighborRealTimeStore)
      .key(key)
      .data(valueToSave)
    oxygenDataAccess.setValue(newKeyValueEntry,withVersion,version)
  }

  def saveNearNeighbor(key: String, ti: TaggedIdentity): Unit = {
    // check if this pin exists in real-time store
//    val existingNearNeighbors = readNearNeighborFromRealTimeStore(key)
    val keyValueEntry = new KeyValueEntry()
      .table(nearNeighborRealTimeStore)
      .key(key)

    val retryInterval = 1 // millisecond
    val nTry = 10000
    var iTry = 0
    while ( {
      val isWritten = nearNeighborWriteWithVersionTry(ti, key, keyValueEntry, true)
      iTry += 1
      if (iTry>nTry) {
        logger.error("Error: HBase concurrent write timeout - saveNearNeighbor")
      }
      (!isWritten) && (iTry<=nTry)
    }) {Thread.sleep(retryInterval)}
  }

  private def getTaggedIdentityArrayFromJSONString(json: String): Array[TaggedIdentity] = {
    val taggedIdentities = if (json != null) {
      ScalaJacksonUtil.fromJson[Array[TaggedIdentity]](json).get
    } else {
      Array.empty[TaggedIdentity]
    }
    taggedIdentities
  }
}
