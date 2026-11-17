package com.experian.edf.oxygen.pinning.util

object PinGenerator extends Serializable {
  /**
    * Create a unique Pin. The pin is created using a combination of current system timestamp, the partition ID of
    * the partition processing the event and some sequence number which increments. The returned value is a 64 bit
    * number. 41 bits are allocated to the timestamp, partition ID can occupy 10 bits and a 12 bit random number
    *
    * @param index - Partition ID coming from Map partitions
    * @return Unique event ID as Long
    */
  private[oxygen] def createPinId(index: Int): Long = {
    val timestamp = System.currentTimeMillis()
    val id = timestamp << (63 - 41)
    val intermediateId = id | (index << (63 - 41 - 10))
    val eventId = intermediateId | index
    eventId | new java.util.Random().nextInt(4096)
  }

  /**
   * CHeck if the pin id is less than minValueAllowed if it is the case recalculated again.
   * @param index
   * @return
   */
  private [oxygen] def createPin(index:Int , minValueAllowed:Int = 2000, vlePins:Array[Long] = Array.empty[Long]):Long = {
    val tempGeneratedPin = createPinId(index)
    val generatedPin = if ( tempGeneratedPin > minValueAllowed && !vlePins.contains(tempGeneratedPin)) {
      tempGeneratedPin
    } else {
      createPinId(index);
    }
    generatedPin
  }

}
