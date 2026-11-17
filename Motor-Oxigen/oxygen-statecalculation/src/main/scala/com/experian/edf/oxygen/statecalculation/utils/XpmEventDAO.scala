package com.experian.edf.oxygen.statecalculation.utils

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.utils.JsonUtils
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory.getLogger

class XpmEventDAO(oxygenDataAccess: OxygenDataAccess,
                  xpmEventStore: String) extends Serializable {
  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def readXpmEvent(pin: Long) = {
    val keyValueEntry = new KeyValueEntry()
      .table(xpmEventStore)
      .key(pin.toString)

    val value = oxygenDataAccess.getValueAsString(keyValueEntry).orElse(null)

    if (value == null)
      null
    else
      JsonUtils.fromJson(value, classOf[JsonObject])
  }

  def newEventReadWithVersion(keyValueEntry: KeyValueEntry): (String, Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry, true).orElse(null)
    val (rtValue, version) = if (rtValueRaw != null){
      val value = rtValueRaw._2
      val iniVersion: Long = rtValueRaw._1
      (value, iniVersion)
    } else (null, -1L)

    (rtValue, version)
  }

  def newEventWriteWithVersionTry(keyValueEntry: KeyValueEntry,
                                  withVersion: Boolean): Boolean = {
    val (oldKeyValueEntry, version) = newEventReadWithVersion(keyValueEntry)
    oxygenDataAccess.setValue(keyValueEntry, withVersion, version)
  }

  private[oxygen] def saveStateEvents(pin: Long, stateEvent: String, hbaseXpmTableName : String,
                                      oxygenDataAccess: OxygenDataAccess ) = {
    val newKeyValueEntry = new KeyValueEntry()
      .table(xpmEventStore)
      .key(pin.toString)
      .data(stateEvent)

    try {
      val retryInterval = 1 // millisecond
      val nTry = 10000
      var iTry = 0
      while ( {
        val isWritten = newEventWriteWithVersionTry(newKeyValueEntry, true)
        iTry += 1
        if (iTry > nTry) {
          logger.error("Error: HBase concurrent write timeout - saveStateEvent")
        }
        (!isWritten) && (iTry <= nTry)
      }) {
        Thread.sleep(retryInterval)
      }
    } catch {
      case e: Exception => {
        oxygenDataAccess.setValue(newKeyValueEntry)
      }
    }
  }
}
