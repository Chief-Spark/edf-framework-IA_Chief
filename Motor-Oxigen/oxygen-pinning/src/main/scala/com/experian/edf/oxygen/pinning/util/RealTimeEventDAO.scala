package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.domain.TaggedCuratedEvent
import com.experian.edf.oxygen.utils.JsonUtils
import org.slf4j.LoggerFactory.getLogger

class RealTimeEventDAO(oxygenDataAccess: OxygenDataAccess,
                       realTimeEventStore: String) extends Serializable {

  @transient lazy val logger = getLogger(this.getClass)

  private[oxygen] def readEvent(pin: Long): Array[TaggedCuratedEvent] = {
    val keyValueEntry = new KeyValueEntry()
      .table(realTimeEventStore)
      .key(pin.toString)

    val value = oxygenDataAccess.getValueAsString(keyValueEntry).orElse(null)

    if (value == null)
      Array.empty[TaggedCuratedEvent]
    else
      JsonUtils.fromJson(value, classOf[Array[TaggedCuratedEvent]])
  }

  def newEventReadWithVersion(keyValueEntry: KeyValueEntry): ( String, Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry,true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version:Long = if (rtValueRaw != null) rtValueRaw._1 else -1L
    (rtValue, version)
  }

  def newEventWriteWithVersionTry(keyValueEntry: KeyValueEntry,
                                      withVersion:Boolean): Boolean = {
    val (oldKeyValueEntry,version) = newEventReadWithVersion(keyValueEntry)
    oxygenDataAccess.setValue(keyValueEntry, withVersion, version)
  }

  private[oxygen] def saveNewEvent(pin: Long, tce: TaggedCuratedEvent) = {
    val valueToSave = JsonUtils.toJson(Array(tce))

    val newKeyValueEntry = new KeyValueEntry()
      .table(realTimeEventStore)
      .key(pin.toString)
      .data(valueToSave)

    try {
      val retryInterval = 1 // millisecond
      val nTry = 10000
      var iTry = 0
      while ( {
        val isWritten = newEventWriteWithVersionTry(newKeyValueEntry, true)
        iTry += 1
        if (iTry>nTry) {
          logger.error("Error: HBase concurrent write timeout - saveNewEvent")
        }
        (!isWritten) && (iTry<=nTry)
      }) {Thread.sleep(retryInterval)}
    } catch {
      case e: Exception =>
        {
          oxygenDataAccess.setValue(newKeyValueEntry)
        }
    }
  }

  private[oxygen] def eventReadWithVersion(keyValueEntry: KeyValueEntry): (Array[TaggedCuratedEvent], Long) = {
    val rtValueRaw = oxygenDataAccess.getValueAsString(keyValueEntry,true).orElse(null)
    val rtValue = if (rtValueRaw != null) rtValueRaw._2 else null
    val version:Long = if (rtValueRaw != null) rtValueRaw._1 else -1L

    val existingEvents = if (rtValue == null)
      Array.empty[TaggedCuratedEvent]
    else
      JsonUtils.fromJson(rtValue, classOf[Array[TaggedCuratedEvent]])
    (existingEvents, version)
  }

  private[oxygen] def eventWriteWithVersionTry(tce: TaggedCuratedEvent, pin: Long, keyValueEntry: KeyValueEntry,
                                               withVersion:Boolean): Boolean = {
    val (existingEvents,version) = eventReadWithVersion(keyValueEntry)

    val valueToSave = if (existingEvents.isEmpty) {
      JsonUtils.toJson(Array(tce))
    } else {
      JsonUtils.toJson(tce +: existingEvents)
    }

    val newKeyValueEntry = new KeyValueEntry()
      .table(realTimeEventStore)
      .key(pin.toString)
      .data(valueToSave)

    oxygenDataAccess.setValue(newKeyValueEntry,withVersion,version)
  }

  private[oxygen] def saveEvent(pin: Long, tce: TaggedCuratedEvent) = {
    val keyValueEntry = new KeyValueEntry()
      .table(realTimeEventStore)
      .key(pin.toString)

    val retryInterval = 1 // millisecond
    val nTry = 10000
    var iTry = 0
    while ( {
      val isWritten = eventWriteWithVersionTry(tce, pin: Long, keyValueEntry, true)
      iTry += 1
      if (iTry>nTry) {
        logger.error("Error: HBase concurrent write timeout - saveEvent")
      }
      (!isWritten) && (iTry<=nTry)
    }) {Thread.sleep(retryInterval)}
  }
}
