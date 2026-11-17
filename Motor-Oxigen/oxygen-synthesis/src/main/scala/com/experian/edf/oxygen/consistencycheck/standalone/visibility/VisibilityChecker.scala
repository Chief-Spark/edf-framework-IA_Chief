package com.experian.edf.oxygen.consistencycheck.standalone.visibility

import com.experian.edf.oxygen.consistencycheck.domain.CustomRule
import com.experian.edf.oxygen.domain.SyntheticEvent
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.consistencycheck.standalone.custom.CustomRuleCheckParser
import com.experian.edf.oxygen.utils.JsonUtils
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.BufferedSource
import scala.util.control.Breaks

/**
 *
 * @author annapurna - C22603A
 *         Created on 3/21/20 at 10:07 AM
 */
object VisibilityChecker {

  /**
    * ***** Note that this function has side effects.  It changes the curatedRecordJson. *****
    *
    * @param syntheticEvents
    * @param rules
    */
//  [
//  {
//    "name": "HistoricalBlock",
//    "description": "Remove events that has historical block",
//    "check": "mostRecent($isHistoricalBlock.asBoolean) == true",
//    "action": "hide",
//    "precedence": 1
//  },
//  {
//    "name": "DisputeThatHidesONlyCertainFields",
//    "description": "Remove events that has historical block",
//    "check": "mostRecent($isDisputed.asBoolean) == true",
//    "action": "hide",
//    "fields": ["balance"],
//    "precedence": 2
//  },
//  {
//    "name": "UseConstant",
//    "description": "Remove events with account status unchanged",
//    "check": "mostRecent($isDisputed.asBoolean) == true",
//    "action": "hide",
//    "fields": ["balance"],
//    "duration": "(200108.asPeriod : env.CURRENT_DATE_TIME.asPeriod - 204)",
//    "precedence": 3
//  }
//  ]

  private[oxygen] def applyVisibility(syntheticEvents: List[SyntheticEvent], rules: Array[CustomRule], currentDateTime: Long, lookupService: OxygenLookup) = {
    if (rules != null && !rules.isEmpty) {
      // events sorted by .precedence
      val sortedRules = rules.sortBy(x => x.precedence)
      // synthetic events grouped by keys
      val eventsGroupedBySR = groupEventsBySR(syntheticEvents)

      eventsGroupedBySR.foreach(x => {
        val events = x._2.toList

        val visibilityCheckParser = new CustomRuleCheckParser(events, currentDateTime, lookupService)
        val stopOnHideAllEvents = new Breaks
        stopOnHideAllEvents.breakable {
          sortedRules.foreach(rule => {
            // if conditions met
            if (visibilityCheckParser.parseAll(visibilityCheckParser.applyCheck, rule.check).get) {
              // apply visibility to events and fields, considering duration
              applyVisibilityAction(events, rule, currentDateTime, lookupService)

              // No need to apply rest of the rules if this rule hides all the events.
              if (rule.hideAllEvents) stopOnHideAllEvents.break
            }
          })
        }
      })
    }
  }

  /**
   * Applies the visibility condition.
   * ***** Note that this function has side effects.  It changes the curatedRecordJson. *****
   *
   * @param events
   * @param rule
   */
  private def applyVisibilityAction(events: List[SyntheticEvent], rule: CustomRule, currentDateTime: Long, lookupService: OxygenLookup) = {

    rule.action match {
        // case "hide"
      case CustomRule.actionTypeHide => {
        // ** Hide events or fields for given period/duration
        if (rule.duration != null) {
          val visibilityCheckParser = new CustomRuleCheckParser(events, currentDateTime, lookupService)
          // Get (start, end)
          val (start, end) = visibilityCheckParser.parseAll(visibilityCheckParser.applyDurationCustomRule, rule.duration).get

          // if duration exists and fields empty, make event within time span invisible
          if (rule.fields.isEmpty) {
            events.foreach(e => {
              val eventPeriod = e.asOfPeriod.id.toLong
              if (eventPeriod >= start && eventPeriod <= end) {
                makeEventInvisible(e)
              }
            })
          } else {
            // if duration exists and fields exist, make event/field within time span invisible
            events.foreach(e => {
              val eventPeriod = e.asOfPeriod.id.toLong
              if (eventPeriod >= start && eventPeriod <= end) {
                rule.fields.foreach(columnName => if (e.curatedRecordJson.containsKey(columnName)) makeColumnInvisible(e, columnName))
              }
            })
          }
        }
        // ** Hide fields for all events if specified or else hide whole event
        else {
          // if no duration and fields exist, make event/field invisible
          if (rule.fields.nonEmpty) {
            rule.fields.foreach(columnName => {
              events.foreach(e => if (e.curatedRecordJson.containsKey(columnName)) makeColumnInvisible(e, columnName))
            })
          } else {
            // if no duration and no fields, make event invisible
            events.foreach(e => makeEventInvisible(e))
          }
        }
      }
      // more visibility actions to be supported
      case _ => throw new NotImplementedException
    }
  }

  private[oxygen] def makeEventsVisible(events: List[SyntheticEvent]) = {
    events.map(se => se.curatedRecordJson.put("isVisible", true))
  }

  private[oxygen] def makeEventVisible(se: SyntheticEvent) = {
    se.curatedRecordJson.put("isVisible", true)
  }

  private[oxygen] def makeEventsInvisible(events: List[SyntheticEvent]) = {
    events.map(x => makeEventInvisible(x))
  }

  private[oxygen] def makeEventInvisible(se: SyntheticEvent) = {
    se.curatedRecordJson.put("isVisible", false)
  }

  private def makeColumnInvisible(se: SyntheticEvent, columnName: String) = {
    se.curatedRecordJson.put(columnName + ".isVisible", false)
  }

  private[oxygen] def readVisibilityRules(file: String): Array[CustomRule] = {
    val source = scala.io.Source.fromURL(getClass.getClassLoader.getResource(file))
    readVisibilityRules(source)
  }

  private[oxygen] def readVisibilityRules(file: BufferedSource): Array[CustomRule] = {
    val visibilityRulesAsString = file.mkString.replace("\n", "")
    JsonUtils.fromJson(visibilityRulesAsString, classOf[Array[CustomRule]])
  }

  private def getSRKey(se: SyntheticEvent): Int = {
    (se.subscriber + ":" + se.subscriberRelation).hashCode
  }

  private def groupEventsBySR(syntheticEvents: List[SyntheticEvent]): Map[Int, ListBuffer[SyntheticEvent]] = {
    val eventsGroupedBySR = mutable.HashMap[Int, ListBuffer[SyntheticEvent]]()
    syntheticEvents.map(x => {
      val srKey = getSRKey(x)
      val group = eventsGroupedBySR.getOrElse(srKey, {
        val newGroup = ListBuffer[SyntheticEvent]()
        eventsGroupedBySR.put(srKey, newGroup)
        newGroup
      })
      group += x
    })
    eventsGroupedBySR.toMap
  }
}
