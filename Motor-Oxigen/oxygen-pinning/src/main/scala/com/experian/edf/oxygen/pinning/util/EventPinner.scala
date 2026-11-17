package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.domain.pinning.{IndexPins, PinIndex}
import com.experian.edf.oxygen.domain.{TaggedCuratedEvent, TaggedIdentity}
import com.experian.edf.oxygen.domain.internal.Identity
import com.experian.edf.oxygen.hydrator.core.dehydrator.{PinIndexDehydratorService, StampedIdentityDehydratorService}
import org.slf4j.LoggerFactory.getLogger


class EventPinner(identityMatcherClassName: String,
                  identifyingAttributes: Array[String],
                  oxygenDataAccess: OxygenDataAccess,
                  nearNeighborStateStore: String,
                  nearNeighborsDehydrator: NearNeighborsDehydratorService,
                  nearNeighborRealTimeStore: String,
                  stampedIdentityStateStore: String,
                  stampedIdentityDehydrator: StampedIdentityDehydratorService,
                  stampedIdentityRealTimeStore: String,
                  pinIndexDehydrator: PinIndexDehydratorService,
                  pinIndexRealTimeStore: String,
                  realTimeEventStore: String,
                  identityPinMapStore: String,
                  addPinIndex: Boolean) extends Serializable {

  @transient lazy private val logger = getLogger(this.getClass)
  private val identityMatcher = Class.forName(identityMatcherClassName).getConstructor().newInstance().asInstanceOf[IdentityMatcher]

  private val nearNeighborDAO = new NearNeighborDAO(
    oxygenDataAccess,
    nearNeighborStateStore,
    nearNeighborRealTimeStore,
    nearNeighborsDehydrator
  )

  private val realTimeEventDAO = new RealTimeEventDAO(oxygenDataAccess, realTimeEventStore)

  private val stampedIdentityDAO = new StampedIdentityDAO(
    oxygenDataAccess,
    stampedIdentityStateStore,
    stampedIdentityRealTimeStore,
    stampedIdentityDehydrator
  )

  private val pinIndexDAO = new PinIndexDAO(
    oxygenDataAccess,
    pinIndexRealTimeStore,
    pinIndexDehydrator
  )


  private val identityPinMapDAO = new IdentityPinMapDAO(
    oxygenDataAccess,
    identityPinMapStore
  )

  private def addToPinIndex(pin: Long, pinIndexes: Array[PinIndex], eventToPin:TaggedCuratedEvent) = {
    pinIndexes
      .foreach(each => {
        val keys = each.keys
        val attributes = scala.collection.mutable.Map[String, String]()
        keys.map(
          eachKey => {
            val eachVal = eventToPin.curatedRecordJson.get(eachKey)
            val strVal = if(eachVal != null) eachVal.getAsString else ""
            attributes += (eachKey -> strVal)
          }
        )
        val indexId = Identity(attributes.toMap + ("indexName" -> each.name)).identityId.toString

        val (pins, indexFound) = getPinIndex(indexId)
        if(indexFound){
          logger.debug("index found {} pin {}", indexId, pin)
          if(!pins.contains(pin.toString)) {
            val addedPin = pin.toString :: pins
            pinIndexDAO.savePinIndex(IndexPins(indexId, addedPin))
          }
          // index exists for the same pin
          else {
            pinIndexDAO.savePinIndex(IndexPins(indexId, pins))
          }
        }
        else{
          logger.debug("index not found {} pin {}", indexId, pin)
          pinIndexDAO.savePinIndex(IndexPins(indexId,List(pin.toString)))
        }
      })

  }


  private def getPinForIdentities(taggedIdentities: List[TaggedIdentity]): List[(Long, TaggedIdentity)] = {
    taggedIdentities.map(x => (getPinForIdentity(x)._1, x)).filter(_._1 != -1L)
  }

  private def getPinForIdentity(taggedIdentity: TaggedIdentity): (Long, Boolean) = {
    val stampedIdentity = stampedIdentityDAO.readStampedIdentity(taggedIdentity.identity.identityId)
    if (stampedIdentity == null) (-1L, false) else (stampedIdentity.pin, true)
  }

  private def getPinIndex(key: String) = {
    val foundPinIndex = pinIndexDAO.readPinIndex(key)
    if (foundPinIndex == null || foundPinIndex.isEmpty) (null, false) else (foundPinIndex, true)
  }
}
