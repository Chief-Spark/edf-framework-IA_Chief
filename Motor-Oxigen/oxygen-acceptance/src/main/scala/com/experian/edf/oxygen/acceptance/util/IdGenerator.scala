package com.experian.edf.oxygen.acceptance.util

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.security.SecureRandom


object IdGenerator extends Serializable {

  @volatile private var timestamp = System.currentTimeMillis()
  private val sequenceInt = new AtomicLong(0)

  private val totalAvailableBits = 63 // ignore the sign bit.
  private val epochBits = 42
  private val jobIdBits = 21
  private val partitionIdBits = 27
  private val sequenceIdBits = 36

  val maxJobId: Int = (Math.pow(2, jobIdBits) - 1).toInt
  val maxPartitionId: Long = (Math.pow(2, partitionIdBits) - 1).toLong
  val maxSequenceId: Long = (Math.pow(2, sequenceIdBits) - 1).toLong

  def nextEventId(jobId: Int, partitionId: Int): String = {
    assert(jobId <= maxJobId, jobId + " exceeds the maximum parallel jobs.  Must be less than " +  maxJobId)
    assert(partitionId.toLong <= maxPartitionId, partitionId + " exceeds the maximum partition.  Must be less than " + maxPartitionId)

    val nextSequence = sequenceInt.incrementAndGet() % maxSequenceId

    if (nextSequence == 0) {
      Thread.sleep(1)
    }

    timestamp = System.currentTimeMillis()

    val majorPart = timestamp << (totalAvailableBits - epochBits) | jobId
    val minorPart = partitionId.toLong << (totalAvailableBits - partitionIdBits) | nextSequence
    new UUID(majorPart, minorPart).toString
  }

  def getMajorPart(id: String): Long = {
    val uuid = UUID.fromString(id)
    uuid.getMostSignificantBits
  }

  def getMinorPart(id: String): Long = {
    val uuid = UUID.fromString(id)
    uuid.getLeastSignificantBits
  }

  def getMajorMinorParts(id: String): (Long, Long) = {
    val uuid = UUID.fromString(id)
    (uuid.getMostSignificantBits, uuid.getLeastSignificantBits)
  }

  /**
   * Create a unique  ID for each event.
   * The unique id is created using a combination of current system timestamp, the timestamp/partition ID of
   * the partition processing the event and some sequence number which increments. The returned value is a 64 bit
   * number. 41 bits are allocated to the timestamp, partition ID can occupy 10 bits and a 12 bit random number
   *
   * @param index - Partition ID coming from Map partitions / timestamp
   * @return Unique ID as Long
   */
  @deprecated
  private[oxygen] def getUniqueId(index: Long = System.currentTimeMillis()): Long = {
    val timestamp = System.currentTimeMillis()
    val id = timestamp << (63 - 41)
    val intermediateId = id | (index << (63 - 41 - 10))
    val eventId = intermediateId | index
    eventId | new SecureRandom().nextInt(4096)

  }
}