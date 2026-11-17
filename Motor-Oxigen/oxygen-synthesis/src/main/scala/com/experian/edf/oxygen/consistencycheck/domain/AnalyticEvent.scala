package com.experian.edf.oxygen.consistencycheck.domain

import scala.collection.mutable

object AnalyticEvent extends Serializable {
  type Event = mutable.HashMap[String, Any]
}
