package com.experian.edf.oxygen.statecalculation.calculators.utils

import java.math.BigDecimal

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-29 at 17:20
  */
object AggregatesUtils {

  def sum(decimals: List[BigDecimal]): BigDecimal = {
    if (decimals.nonEmpty) decimals.reduce((a, b) => a.add(b))
    else new BigDecimal(0)
  }

  def min(decimals: List[BigDecimal]): BigDecimal = {
    if (decimals.nonEmpty) decimals.min
    else null
  }

  def max(decimals: List[BigDecimal]): BigDecimal = {
    if (decimals.nonEmpty) decimals.max
    else null
  }
}
