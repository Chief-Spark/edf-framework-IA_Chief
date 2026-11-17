package com.experian.edf.oxygen.validation.utils

import com.experian.edf.oxygen.lookup.OxygenLookup

import scala.collection.mutable.ListBuffer

/**
 * Created by c15661a on 8/7/17.
 */
object Util {
  def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

  def toLong(s: String): Option[Long] = {
    try {
      Some(s.toLong)
    } catch {
      case e: Exception => None
    }
  }

  def generateTrue90PercentProbability : Boolean = {
    val rand = new java.util.Random()
    val r = rand.nextInt
    val m = r % 10
    if (m < 9)
      true
    else
      false
  }

  /**
   * Lookup util method to retrieve list of values from the table.
   * @param tablename: name of the table
   * @param lookUpBy: Map of key name and its value to lookup
   * @param selectField: Field in the table to be filtered
   * @return list of values filtered based on the selected field
   */
  def getLookup(tablename: String, lookUpBy: Map[String, String], selectField: String, lookupService: OxygenLookup) :List[String]  = {
    val valueList = lookupService.get(tablename,lookUpBy)
    val resultMap = valueList.map(resultMap => resultMap.map(y => y.get(selectField)))
    val resultList = ListBuffer[String]()
    resultMap match {
      case Some(s) => s.foreach(f =>
        if (f != null && f != None && f.get != null) {
          resultList += f.get.toString
        }
        else {
          resultList += ""
        })
      case None => resultList += ""
    }
    // resultList.foreach(println)
    resultList.toList

  }


}
