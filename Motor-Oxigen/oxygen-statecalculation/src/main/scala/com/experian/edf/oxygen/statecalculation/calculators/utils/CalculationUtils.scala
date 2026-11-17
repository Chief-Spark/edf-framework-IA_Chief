package com.experian.edf.oxygen.statecalculation.calculators.utils

import com.experian.edf.oxygen.global.domain.Datatype
import com.experian.edf.oxygen.global.utils.OxygenCollectionsUtils

import scala.util.Try

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2019-04-30 at 12:38
 */
object CalculationUtils {
  def toDataType(datatype: Datatype, value: Any): Any = {
    if (value != null)
      datatype match {
        case Datatype.COMPLEX => OxygenCollectionsUtils.asDeepJava(value)
        case _ => toDataType(datatype, value.toString.trim)
      }
    else null
  }

  def toDataType(datatype: Datatype, value: String): Any = {
    if (value != null) {
      datatype match {
        case Datatype.BOOLEAN => if (value.equalsIgnoreCase("1") || value.equalsIgnoreCase("true")) true else false
        case Datatype.FLOAT => Try(value.toFloat).getOrElse(null)
        case Datatype.DOUBLE => Try(new java.math.BigDecimal(value)).getOrElse(null)
        // Note: There are occasions where an int will be requested but the raw
        // value is a decimal. If we request an int directly (value.toInt)
        // but the value is a decimal representation (i.e. 123.456) then we
        // get a NumberFormatException. As such, we convert to a double first
        // and then to an int.
        case Datatype.INTEGER => Try(value.toDouble.toInt).getOrElse(null)
        case Datatype.LONG => Try(value.toDouble.toLong).getOrElse(null)
        case Datatype.STRING => value
        case _ => value
      }
    } else null
  }

  // todo: why doesn't this just do map.getOrElse(key, null) - or why doesn't
  // calling code do this instead?
  def getValueFromMap(map: Map[String, Any], key: String): Any = try map(key) catch {
    case _: Throwable => null
  }

  def getAnyRefFromMap(map: Map[String, AnyRef], key: String): Any = try map(key) catch {
    case _: Throwable => null
  }
}
