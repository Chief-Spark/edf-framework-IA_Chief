package com.experian.edf.oxygen.consistencycheck.domain

import com.experian.edf.oxygen.domain.internal.SyntheticEvent

import scala.collection.mutable.ListBuffer

class ReferenceListCache(val referenceCache: Map[String,ReferenceCache]) {


  val referenceCacheMap = new java.util.HashMap[String, (List[String],java.util.HashMap[Int,ListBuffer[SyntheticEvent]])]()


  private[oxygen] def getKeyForListOfStrings(itemList: List[String]): Int = {
    itemList.mkString(":").hashCode
  }

  private[oxygen] def update(syntheticEvent: SyntheticEvent ) = {

  }


  private[oxygen] def get() = {

  }
}
