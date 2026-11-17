package com.experian.edf.oxygen.statecalculation.calculators.calculated.types.impl

import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils
import com.experian.edf.oxygen.statecalculation.{FoundationRecord, FoundationRecordList}
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.{CalculationOptions, ObjectCalculationType}
import com.experian.edf.oxygen.statecalculation.config.calculation.CalculationSection


/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-09 at 12:26
  */
class MostFrequentValue extends ObjectCalculationType("MOST_FREQUENT_VALUE", CalculationOptions()) {

  override def computeObjectType(section: CalculationSection, records: FoundationRecordList): FoundationRecord = {
    val selects = OxygenCollectionsUtils.asScala(section.select())
    selects
      .map(field => field -> records.map(record => record(field)))
      .map(field => field._1 -> getFrequentValue(field._2))
      .toMap
  }

  private def getFrequentValue(list: List[Any]): Any = {
    val map = list.filter(_ != null).foldLeft(Map.empty[Any, Int].withDefaultValue(0)) {
      case (m, v) => m.updated(v, m(v) + 1)
    }
    if (map.isEmpty) null else map.maxBy(_._2)._1
  }

}
