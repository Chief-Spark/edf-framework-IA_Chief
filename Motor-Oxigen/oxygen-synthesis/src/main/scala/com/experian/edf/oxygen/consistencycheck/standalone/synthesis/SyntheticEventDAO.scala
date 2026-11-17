package com.experian.edf.oxygen.consistencycheck.standalone.synthesis

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.dataaccess.model.KeyValueEntry
import com.experian.edf.oxygen.domain.SyntheticEvent
import com.experian.edf.oxygen.consistencycheck.standalone.dyhydrator.SynthesizedEventsDehydratorService
import com.experian.edf.oxygen.utils.{JsonUtils, ScalaJacksonUtil}

class SyntheticEventDAO(
                         oxygenDataAccess: OxygenDataAccess,
                         syntheticEventStore: String,
                         syntheticEventRealTimeStore: String,
                         syntheticEventDehydrator: SynthesizedEventsDehydratorService
                       ) extends Serializable {

  private[oxygen] def readSyntheticEvents(pin: Long): Array[SyntheticEvent] = {

    // first read from real-time store
    val rtKeyValueEntry = new KeyValueEntry()
      .table(syntheticEventRealTimeStore)
      .key(pin.toString)

    val rtValue = oxygenDataAccess.getValueAsString(rtKeyValueEntry).orElse(null)

    val syntheticEvents = if (rtValue != null) {
      JsonUtils.fromJson(rtValue, classOf[Array[SyntheticEvent]])
    } else {
      syntheticEventDehydrator.dehydrate(String.valueOf(pin))
    }
    syntheticEvents
  }

  private[oxygen] def saveSyntheticEvents(pin: Long, syntheticEvents: Array[SyntheticEvent]) = {
    val keyValueEntry = new KeyValueEntry()
      .table(syntheticEventRealTimeStore)
      .key(pin.toString)
      .data(ScalaJacksonUtil.toJson(syntheticEvents).get)

    oxygenDataAccess.setValue(keyValueEntry)
  }

  private[oxygen] def saveSyntheticEventsInBatchStore(pin: Long, syntheticEvents: Array[SyntheticEvent]) = {
    val keyValueEntry = new KeyValueEntry()
      .table(syntheticEventStore)
      .key(pin.toString)
      .data(ScalaJacksonUtil.toJson(syntheticEvents).get)

    oxygenDataAccess.setValue(keyValueEntry)
  }
}




