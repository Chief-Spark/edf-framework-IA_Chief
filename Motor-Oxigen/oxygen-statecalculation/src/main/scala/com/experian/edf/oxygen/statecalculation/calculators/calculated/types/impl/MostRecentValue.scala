package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.impl

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.statecalculation.calculators.calculated.QueryFunctions.select
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.{CalculationOptions, ObjectCalculationType}
import com.experian.edf.oxygen.statecalculation.config.calculation.{CalculationSection, PluggableConfiguration}

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 12:26
  */
class MostRecentValue(name: String = "MOST_RECENT_VALUE", options: CalculationOptions = CalculationOptions(preSelect = false)) extends ObjectCalculationType(name, options) {

  override def computeObjectType(section: CalculationSection, records: FoundationRecordList): FoundationRecord = {
    val priorityConfig: MostRecentValueConfig = Option(getPluginConfiguration[MostRecentValueConfig](section)).getOrElse(MostRecentValueConfig())

    val selects = OxygenCollectionsUtils.asScala(section.select())
    val preSelects = select(records, selects ::: List(priorityConfig.recentBy))
    val mostRecentValue = preSelects
      .sortBy(event => event(priorityConfig.recentBy).toString.toLong)(Ordering[Long].reverse)
      .head
    select(mostRecentValue, selects)
  }

  override def configurationModel: Class[_ <: PluggableConfiguration] = classOf[MostRecentValueConfig]

  case class MostRecentValueConfig(recentBy: String = "asOfPeriod") extends PluggableConfiguration

}