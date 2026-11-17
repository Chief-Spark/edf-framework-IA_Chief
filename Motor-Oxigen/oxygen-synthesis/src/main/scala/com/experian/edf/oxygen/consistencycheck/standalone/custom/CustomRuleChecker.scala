package com.experian.edf.oxygen.consistencycheck.standalone.custom

import com.experian.edf.oxygen.consistencycheck.domain.CustomRule
import com.experian.edf.oxygen.domain.SyntheticEvent
import com.experian.edf.oxygen.lookup.OxygenLookup
import com.experian.edf.oxygen.utils.JsonUtils
import org.slf4j.LoggerFactory.getLogger
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
object CustomRuleChecker {

  @transient lazy val logger = getLogger(this.getClass)

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

  private[oxygen] def applyCustomRule(syntheticEvents: List[SyntheticEvent], rules: Array[CustomRule], currentDateTime: Long, lookupService: OxygenLookup) = {
    if (rules != null && !rules.isEmpty) {
      // events sorted by .precedence
      val sortedRules = rules.sortBy(x => x.precedence)
      // synthetic events grouped by keys
      val eventsGroupedBySR = groupEventsBySR(syntheticEvents)

      eventsGroupedBySR.foreach(x => {
        val events = x._2.toList

        val customRuleCheckParser = new CustomRuleCheckParser(events, currentDateTime, lookupService)
        val stopOnActionAllEvents = new Breaks
        stopOnActionAllEvents.breakable {
          sortedRules.foreach(rule => {
            // if conditions met
            if (customRuleCheckParser.parseAll(customRuleCheckParser.applyCheck, rule.check).get) {
              // apply customRule to events and fields, considering duration
              applyCustomRuleAction(events, rule, currentDateTime, lookupService)

              // No need to apply rest of the rules if this rule hides all the events.
              if (rule.hideAllEvents) stopOnActionAllEvents.break
            }
          })
        }
      })
    }
  }

  /**
   * Applies the customRule condition.
   * ***** Note that this function has side effects.  It changes the curatedRecordJson. *****
   *
   * @param events
   * @param rule
   */
  private def applyCustomRuleAction(events: List[SyntheticEvent], rule: CustomRule, currentDateTime: Long, lookupService: OxygenLookup) = {

    rule.action match {
      // case "hide / delete"
      case CustomRule.actionTypeHide | CustomRule.`actionTypeSetFlag` => {
        // ** Hide events or fields for given period/duration
        if (rule.duration != null) {
          val customRuleCheckParser = new CustomRuleCheckParser(events, currentDateTime, lookupService)
          // Get (start, end)
          val (start, end) = customRuleCheckParser.parseAll(customRuleCheckParser.applyDurationCustomRule, rule.duration).get

          // if duration exists and fields empty, make event within time span invisible
          if (rule.fields.isEmpty) {
            events.foreach(e => {
              val eventPeriod = e.asOfPeriod.id.toLong
              if (eventPeriod >= start && eventPeriod <= end) {
                if(rule.action.equals(CustomRule.actionTypeHide)) makeEventInvisible(e)
                else if (rule.action.equals(CustomRule.actionTypeSetFlag)) makeEventCustomFlagged(e, rule.newFlagName)
                else logger.error(s"No valid action is set for customRule. Please set it to ${CustomRule.actionTypeHide} / ${CustomRule.actionTypeSetFlag}")
              }
            })
          } else {
            // if duration exists and fields exist, make event/field within time span invisible
            events.foreach(e => {
              val eventPeriod = e.asOfPeriod.id.toLong
              if (eventPeriod >= start && eventPeriod <= end) {
                rule.fields.foreach(columnName =>
                  if (e.curatedRecordJson.containsKey(columnName) && rule.action.equals(CustomRule.actionTypeHide)) makeColumnInvisible(e, columnName)
                  else if (e.curatedRecordJson.containsKey(columnName) && rule.action.equals(CustomRule.actionTypeSetFlag)) makeColumnDeletable(e, columnName)
                  else logger.error(s"No valid action is set for customRule. Please set it to ${CustomRule.actionTypeHide} / ${CustomRule.actionTypeSetFlag}")
                )
              }
            })
          }
        }
        // ** Hide fields for all events if specified or else hide whole event
        else {
          // if no duration and fields exist, make event/field invisible
          if (rule.fields.nonEmpty) {
            rule.fields.foreach(columnName => {
              events.foreach(e =>
              {
                if (e.curatedRecordJson.containsKey(columnName) && rule.action.equals(CustomRule.actionTypeHide)) makeColumnInvisible(e, columnName)
                else if (e.curatedRecordJson.containsKey(columnName) && rule.action.equals(CustomRule.actionTypeSetFlag)) makeColumnDeletable(e, columnName)
                else logger.error(s"No valid action is set for customRule. Please set it to ${CustomRule.actionTypeHide} / ${CustomRule.actionTypeSetFlag}")
              })
            })
          } else {
            // if no duration and no fields, make event invisible/deletable
            events.foreach(e => {
              if(rule.action.equals(CustomRule.actionTypeHide)) makeEventInvisible(e)
              else if (rule.action.equals(CustomRule.actionTypeSetFlag)) makeEventCustomFlagged(e, rule.newFlagName)
              else logger.error(s"No valid action is set for customRule. Please set it to ${CustomRule.actionTypeHide} / ${CustomRule.actionTypeSetFlag}")
            })
          }
        }
      }

      case _ => {
        logger.error(s"No valid action is set for customRule. Please set it to ${CustomRule.actionTypeHide} / ${CustomRule.actionTypeSetFlag}")
        throw new NotImplementedException
      }
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

  private[oxygen] def makeEventCustomFlagged(se: SyntheticEvent, newFlagName: String) = {
    se.curatedRecordJson.put(newFlagName, true)
  }

  private def makeColumnInvisible(se: SyntheticEvent, columnName: String) = {
    se.curatedRecordJson.put(columnName + ".isVisible", false)
  }

  private def makeColumnDeletable(se: SyntheticEvent, columnName: String) = {
    se.curatedRecordJson.put(columnName + ".isDeleted", true)
  }

  private[oxygen] def readCustomRules(file: String): Array[CustomRule] = {
    val source = scala.io.Source.fromURL(getClass.getClassLoader.getResource(file))
    readCustomRules(source)
  }

  private[oxygen] def readCustomRules(file: BufferedSource): Array[CustomRule] = {
    val customRulesAsString = file.mkString.replace("\n", "")
    JsonUtils.fromJson(customRulesAsString, classOf[Array[CustomRule]])
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
