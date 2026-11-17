package com.experian.edf.oxygen.consistencycheck.standalone.consistencycheck

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}
import com.experian.edf.oxygen.consistencycheck.domain.{StateAttributes, StateEvent, StateEventField}
import com.experian.edf.oxygen.global.utils.Utils
import com.experian.edf.oxygen.consistencycheck.standalone.SynthesisConstants
import org.slf4j.LoggerFactory.getLogger

import scala.collection.mutable
import scala.collection.mutable.{HashMap, ListBuffer}

class StateCheck(stateAttribute: StateAttributes) extends Serializable {

  @transient lazy val logger = getLogger(this.getClass)

  private val stateCheckFields = this.getAllStateFields(stateAttribute)
  private val fieldNameMapStateFields = HashMap[String, HashMap[Int, StateEventField]]()
  // To maintain multiple states under the same stateid
  private val fieldMapWithMultipleStates = mutable.HashMap[String, HashMap[Int, List[StateEventField]]]()
  private val emptyFieldNameMapStateFields = mutable.HashMap.empty[Int, List[StateEventField]]

  // To maintain all the events for that key, so that when an event gets filtered out the table can be reconstructed
  private val stateEventList = mutable.HashMap[Int, ListBuffer[StateEvent]]()

  private[oxygen] def getAllStateFields(stateDict: StateAttributes): Map[(String, String), String] = {
    stateDict.fieldsMap
  }

  private[oxygen] def getStateCheckFields: Map[(String, String), String] = {
    stateCheckFields
  }

  private[oxygen] def getFieldNameMapStateFields: mutable.HashMap[String, mutable.HashMap[Int, StateEventField]] = {
    fieldNameMapStateFields
  }

  private[oxygen] def getFieldNameMapWithMultipleStates: mutable.HashMap[String, mutable.HashMap[Int, List[StateEventField]]] = {
    fieldMapWithMultipleStates
  }

  //  private[oxygen] def getStateFields(fieldNameMap: HashMap[String, HashMap[Int, StateEventField]], fieldName: String) = {
  private[oxygen] def getStateFields(fieldName: String): mutable.HashMap[Int, StateEventField] = {
    val stateField = if (getFieldNameMapStateFields.get(fieldName).isDefined) getFieldNameMapStateFields.get(fieldName).get else HashMap.empty[Int, StateEventField]
    stateField
  }

  private[oxygen] def getStateFieldsList(fieldName: String): mutable.HashMap[Int, List[StateEventField]] = {
    val stateField = if (getFieldNameMapWithMultipleStates.contains(fieldName)) getFieldNameMapWithMultipleStates(fieldName) else emptyFieldNameMapStateFields
    stateField
  }

  private[oxygen] def getSRSKey(f: String, sid: String, sub: String, relation: String): Int = {
    logger.debug(s"getSRSKey for f {}, sid {}, sub {}, relation {}", f, sid, sub, relation)
    (f + ":" + sid + ":" + sub + ":" + relation).hashCode
  }

  private[oxygen] def isStateOpen(stateField: HashMap[Int, StateEventField], stateFieldName: String, thisDate: Long, key: Int): Boolean = {
    try {
      val cachedValueState = stateField.get(key).orElse(None)

      val result = if (cachedValueState == None) true // should accept
      else {
        val someCachedStateValue = cachedValueState.get
        val openDate = if (someCachedStateValue.openDate.isEmpty) -1L else someCachedStateValue.openDate.get
        val closeDate = if (someCachedStateValue.closedDate == None || someCachedStateValue.closedDate.get == -1L) -1L else someCachedStateValue.closedDate.get

        val doesCorrespondCloseDateExists =
          if (closeDate == None || closeDate == -1L)
            false
          else true

        val isInHold = if (doesCorrespondCloseDateExists == false && thisDate >= openDate // hold was open but closed data not received
          || (openDate != -1 && openDate != None && (thisDate >= openDate && thisDate <= closeDate))
        ) true
        else false

        val shouldAccept = if (isInHold) false else true
        shouldAccept
      }
      logger.debug(s"Should accept result {}, stateFieldName {}, of receivedDate {}", result.toString, stateFieldName, thisDate.toString)
      result
    } catch {
      case e: Exception => {
        logger.error("Exception on checking on hold StateCheck exception of {}, stateCheckFieldName {}, of receivedDate {}", e, stateFieldName, thisDate.toString)
        false
      }
    }
  }

  private[oxygen] def update(x: TaggedCuratedEventWithMap) = {
    stateCheckFields.foreach(f => {
      val isStateClosed = if (Utils.checkValueExistsAndNonNull(x.curatedRecordJson,f._1._1)) x.curatedRecordJson.get(f._1._1) else None
      if (x.curatedRecordJson.containsKey(f._2)) {
        val stateName = x.curatedRecordJson.get(f._2).toString
        // updating the stateEventList for the key, so that the sorted events can be checked for open and close dates
        updateStateEventList(x.eventId, f._1._1, stateName, isStateClosed, x.asOfDate, x.subscriber, x.subscriberRelation)
        //updateStateFieldValueList(f._1._1, stateName, isStateClosed, x.asOfDate, x.subscriber, x.subscriberRelation)
      }
    })
  }

  private[oxygen] def update(x: SyntheticEvent) = {
    stateCheckFields.foreach(f => {
      val isStateClosed = if (Utils.checkValueExistsAndNonNull(x.curatedRecordJson, f._1._1)) {
        x.curatedRecordJson.get(f._1._1)
      } else {
        logger.debug(s"cannot get value ${f._1._1} from record ${x.curatedRecordJson}")
        None
      }

      if (Utils.checkValueExistsAndNonNull(x.curatedRecordJson, f._2)) {
        val stateName = x.curatedRecordJson.get(f._2).toString
        updateStateFieldValueList(f._1._1, stateName, isStateClosed, x.asOfPeriod.epochDateTime, x.subscriber, x.subscriberRelation)
      }
      else{
        logger.debug(s"cannot get value ${f._2} from record ${x.curatedRecordJson}")
      }
    })
  }

  private[oxygen] def getOpenOrCloseDates(cachedOpenOrClosedDate: Option[StateEventField], openOrCloseFlag: String): Long = {
    if (openOrCloseFlag == SynthesisConstants.OPEN) {
      if (cachedOpenOrClosedDate == None || cachedOpenOrClosedDate.get.openDate == None || cachedOpenOrClosedDate.get.openDate.get == -1L) -1L else cachedOpenOrClosedDate.get.openDate.get
    }
    else {
      if (cachedOpenOrClosedDate == None || cachedOpenOrClosedDate.get.closedDate == None || cachedOpenOrClosedDate.get.closedDate.get == -1L) -1L else cachedOpenOrClosedDate.get.closedDate.get
    }

  }

  private[oxygen] def updateStateFieldValue(stateName: String,
                                            stateId: String,
                                            isStatusOpen: Any,
                                            asOfDate: Long,
                                            subscriber: String,
                                            subscriberRelation: String) = {
    val stateField = if (fieldNameMapStateFields.get(stateName) != None) fieldNameMapStateFields.get(stateName).get else HashMap.empty[Int, StateEventField]
    val key = getSRSKey(stateName, stateId, subscriber, subscriberRelation)
    val cachedValue = stateField.get(key).orElse(None)

    try {
      // if value has something to update
      if (isStatusOpen != None) {
        // if dispute is open
        if (isStatusOpen == true) {
          // if no record found
          if (cachedValue == None) {
            stateField.put(key, StateEventField(stateName, stateId, Some(asOfDate), None))
            fieldNameMapStateFields.put(stateName, stateField)
          }
          else {
            val stateCloseDates = getOpenOrCloseDates(cachedValue, SynthesisConstants.CLOSE)
            //if (stateCloseDates != -1)
            stateField.put(key, StateEventField(stateName, stateId,
              Some(asOfDate), Some(stateCloseDates)))
          }
        }
        // else dispute is close
        else {
          val stateOpenDate = getOpenOrCloseDates(cachedValue, SynthesisConstants.OPEN)
          stateField.put(key, StateEventField(stateName, stateId, Some(stateOpenDate), Some(asOfDate)))
          fieldNameMapStateFields.put(stateName, stateField)
        }
      }
    } catch {
      case e: Exception => logger.error("Exception happened during stateCheck update with " + e)
    }
  }

  private[oxygen] def returnListOfOpenStates(cachedOpenOrClosedDate: List[StateEventField]): List[StateEventField] = {
    cachedOpenOrClosedDate.filter(x => (x.closedDate.contains(-1L) || (x.closedDate.isDefined && x.openDate.contains(-1L))))
  }

  private[oxygen] def checkIfOnlyOneCloseWithoutOpenExists(cachedOpenOrClosedDate: List[StateEventField]): Boolean = {
    val closeWithoutOpenList = cachedOpenOrClosedDate.filter(x => (x.openDate.contains(-1L)))
    closeWithoutOpenList.nonEmpty && closeWithoutOpenList.length == 1
  }

  private[oxygen] def checkIfOnlyOneOpenWithoutCloseExists(cachedOpenOrClosedDate: List[StateEventField]): Boolean = {
    val openWithoutCloseList = cachedOpenOrClosedDate.filter(x => (x.closedDate.contains(-1L)))
    openWithoutCloseList.nonEmpty && openWithoutCloseList.length == 1
  }

  private[oxygen] def checkOpenWithoutCloseExists(cachedOpenOrClosedDate: List[StateEventField]): List[StateEventField] = {
    val openWithoutCloseList = cachedOpenOrClosedDate.filter(x => (x.closedDate.contains(-1L)))
    openWithoutCloseList
  }


  private def updateStateEventList(eventId: String,
                                   stateName: String,
                                   stateId: String,
                                   isStatusOpen: Any,
                                   asOfDate: Long,
                                   subscriber: String,
                                   subscriberRelation: String): Unit = {
    val stateEvent = StateEvent(eventId, stateName, stateId, isStatusOpen, asOfDate, subscriber, subscriberRelation)
    val key = getSRSKey(stateName, stateId, subscriber, subscriberRelation)
    val cachedValue = stateEventList.get(key)
    if (cachedValue.isDefined && cachedValue.get.nonEmpty) {
      val cachedStateList = cachedValue.get
      cachedStateList.append(stateEvent)
      stateEventList.put(key, cachedStateList)
    } else
      stateEventList.put(key, ListBuffer(stateEvent))
  }

  /**
   * This method is to check if the event is present in eventList and filter it from the the state table
   * For example, If there are list of events as follows:
   * 1. May 11 - StateOpen1
   * 2. May 13 - StateClose1
   * 3. Aug 1  - StateOpen2
   * 4. Aug 2  - StateClose2
   *
   * The initial StateFieldMap is constructed with two entries
   * State1 - open (May11) close (May13)
   * State2 - open (Aug1)  close(Aug2)
   *
   * But due to inconsistencies, say event 2 and event 3 are filtered out. So each time an event is filtered out
   * the state table has to be reconstructed as the open and close states may changes as the events are not present
   * in the system anymore.
   * Following two methods to check if the event is present in eventList and filter it from the the state table
   *
   */

  private[oxygen] def updateAfterFilteringEvent(x: TaggedCuratedEventWithMap): Unit = {
    stateCheckFields.foreach(f => {
      // checking if the event contains the stateId and stateName and filter,if not present do nothing
      if (x.curatedRecordJson.containsKey(f._2) && x.curatedRecordJson.containsKey(f._1._1)) {
        val stateId = x.curatedRecordJson.get(f._2).toString
        val stateName = f._1._1
        removeEventAndRefreshStateList(x.eventId, stateName, stateId, x.subscriber, x.subscriberRelation)
      }
    })
  }


  private def removeEventAndRefreshStateList(eventId: String,
                                             stateName: String,
                                             stateId: String,
                                             subscriber: String,
                                             subscriberRelation: String): Unit = {
    val key = getSRSKey(stateName, stateId, subscriber, subscriberRelation)
    // get the list of events using key from the stateEventList
    val cachedValue = stateEventList.get(key)
    if (cachedValue.isDefined && cachedValue.get.nonEmpty) {
      // remove the event as it has been filtered out due to inconsistency and sort the events
      val listAfterRemovingEvent = cachedValue.get.filterNot(x => x.eventId == eventId).sortBy(_.asOfDate)
      stateEventList.put(key, listAfterRemovingEvent)
      // remove the entry for the key as we will be reconstructing the table
      val stateField = if (fieldMapWithMultipleStates.contains(stateName)) fieldMapWithMultipleStates(stateName) else emptyFieldNameMapStateFields
      stateField.remove(key)
      listAfterRemovingEvent.foreach(each => {
        updateStateFieldValueList(each.stateName, each.stateId, each.isStatusOpen, each.asOfDate, each.subscriber, each.subscriberRelation)
      })
    }
  }

  private[oxygen] def createStateEventTable(): Unit = {
    stateEventList.foreach(each => {
      val stateList = each._2.sortBy(_.asOfDate)
      stateList.foreach(x => updateStateFieldValueList(x.stateName, x.stateId, x.isStatusOpen, x.asOfDate, x.subscriber, x.subscriberRelation)
      )
    })
  }

  // TODO: is to associate eventID infuture we can check if the event is filtered out and remove it form the table
  // TODO: also associate a particular id to each internal state so that it doesnt have to be sequential.
  //  Currently we are opening and closing in a sequential order
  // TODO: We re always adding as of date and not considering received dates for open and close states
  /**
   *
   * // checking if all the entries in the list have both open and close state, only if the state is closed the next is opened
   * // not sure if we want this kind of check, if not how can we know which state to be closed
   * // one reason to have it sequential (open and close) is that we are not associating each internal
   * // states (states with same stateid) with any other identifier. If any bu needs, they can actually use the stateids itself
   * // Also, if there is only one close date without open date then that is added
   */
  private[oxygen] def updateStateFieldValueList(stateName: String,
                                                stateId: String,
                                                isStatusOpen: Any,
                                                asOfDate: Long,
                                                subscriber: String,
                                                subscriberRelation: String): Any = {
    val stateField = if (fieldMapWithMultipleStates.contains(stateName)) fieldMapWithMultipleStates(stateName) else emptyFieldNameMapStateFields
    val key = getSRSKey(stateName, stateId, subscriber, subscriberRelation)
    val cachedValue = stateField.get(key).orElse(None)
    logger.debug(s"stateName: ${stateName} - retrieved : ${cachedValue} ")

    try {
      // if value has something to update
      if (isStatusOpen != None) {
        // if dispute is open
        if (isStatusOpen == true) {
          // if no record found
          if (cachedValue.isEmpty) {
            stateField.put(key, List(StateEventField(stateName, stateId, Some(asOfDate), Some(-1L))))
            fieldMapWithMultipleStates.put(stateName, stateField)
            logger.debug(s"open state: ${stateName} updated state: ${fieldMapWithMultipleStates}")
          }
          else {
            val currentList = cachedValue.get
            val stateCloseDates = returnListOfOpenStates(currentList)
            if (stateCloseDates.isEmpty) {
              val currentListBuffer = currentList.to[ListBuffer]
              val newStateEntry = StateEventField(stateName, stateId, Some(asOfDate), Some(-1L))
              //prepending to the list so that we always check for if head is open
              newStateEntry +=: currentListBuffer
              stateField.put(key, currentListBuffer.toList)
            }
            // else if only closedate exists but open date is not entered ...not a very likely scenario but covering it
            // In this case expecting only one date to be entered
            else if (checkIfOnlyOneCloseWithoutOpenExists(currentList)) {
              val updatedList = currentList.map(x => if (x.openDate.get == -1L && asOfDate < x.closedDate.get) StateEventField(x.name, x.stateIdentifier, Some(asOfDate), x.closedDate) else x)
              stateField.put(key, updatedList)
            }
            // else do nothing
          }
        }
        // else dispute is close
        else {
          val currentList = if (cachedValue.isEmpty) List.empty else cachedValue.get

          // if already close date exists not updating
          val updatedList = if (currentList.nonEmpty) {
            //if only one open date exists
            if (checkOpenWithoutCloseExists(currentList).length == 1)
              currentList.map(x => if (x.closedDate.get == -1L && x.openDate.get < asOfDate) StateEventField(x.name, x.stateIdentifier, x.openDate, Some(asOfDate)) else x)
            //if list is empty, that means all existing states have been closed and no open states exists
            else if (checkOpenWithoutCloseExists(currentList).isEmpty && returnListOfOpenStates(currentList).isEmpty) {
              val currentListBuffer = currentList.to[ListBuffer]
              val newStateEntry = StateEventField(stateName, stateId, Some(-1L), Some(asOfDate))
              currentListBuffer.append(newStateEntry)
              currentListBuffer.toList
            }
            else //do nothing, not adding if there are multiple open states
              currentList
          } //add close date entry only
          else List(StateEventField(stateName, stateId, Some(-1L), Some(asOfDate)))
          stateField.put(key, updatedList)
          fieldMapWithMultipleStates.put(stateName, stateField)
          logger.debug(s"closed state: ${stateName} updated state: ${fieldMapWithMultipleStates}")
        }
      }
    } catch {
      case e: Exception => logger.error("Exception happened during stateCheck update with " + e)
    }
  }


  private[oxygen] def isNewStateOpen(stateField: HashMap[Int, List[StateEventField]], stateFieldName: String, thisDate: Long, key: Int): Boolean = {
    try {
      val cachedValueState = stateField.get(key).orElse(None)
      logger.debug(s"retrieved state - ${cachedValueState}")

      val result = if (cachedValueState.isEmpty) true // should accept
      else {
        val someCachedStateValue = cachedValueState.get
        val filteredList = someCachedStateValue.filter(x => ({
          val openDate = x.openDate.get
          val closeDate = x.closedDate.get
          val doesCorrespondCloseDateExists = if (closeDate == -1L) false else true
          val doesCorrespondOpenDateExists = if (openDate == -1L) false else true

          val out = (thisDate > openDate && !doesCorrespondCloseDateExists) ||
            (openDate != -1 && thisDate > openDate && thisDate <= closeDate)
          logger.debug(s"thisDate: $thisDate > openDate: ${openDate} && !doesCorrespondCloseDateExists ${!doesCorrespondCloseDateExists} || (openDate: ${openDate} != -1 && thisDate: ${thisDate} > openDate: ${openDate} && thisDate: ${thisDate} <= closeDate: ${closeDate})")
          out
        }))
        logger.debug(s"filteredList: ${filteredList}")
        //        assert(filteredList.length == 1 || filteredList.isEmpty)
        if(!(filteredList.length == 1 || filteredList.isEmpty)) {
          logger.error(s"error on filteredList.length: ${filteredList.length} == 1 || filteredList.isEmpty: ${filteredList.isEmpty}")
        }
        val isStateOpen = if (filteredList.nonEmpty) true else false

        val shouldAccept = if (isStateOpen) false else true
        logger.debug(s"isStateOpen: ${isStateOpen} shouldAccept: ${shouldAccept} ")
        shouldAccept
      }
      logger.debug(s"Should accept result {}, stateFieldName {}, of receivedDate {}", result.toString, stateFieldName, thisDate.toString)
      result
    } catch {
      case e: Exception => {
        logger.error("Exception on checking on hold StateCheck exception of {}, stateCheckFieldName {}, of receivedDate {}", e, stateFieldName, thisDate.toString)
        false
      }
    }
  }


}

