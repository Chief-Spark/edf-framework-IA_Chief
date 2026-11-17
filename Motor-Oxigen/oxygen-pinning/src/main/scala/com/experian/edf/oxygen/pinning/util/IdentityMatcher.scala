package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.domain.TaggedIdentity


abstract class IdentityMatcher extends Serializable {
  /**
    * List of keys that are used for find identities that are potentially matches
    *
    * @param taggedIdentity taggedIdentity
    * @return list of near-neighbor keys
    */
  def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = {
    List(taggedIdentity.subscriber + ":" + taggedIdentity.subscriberRelation)
  }

  /**
   * Default near-neighbor key (SSR) that is used for find identities that are potentially matches
   *
   * @param taggedIdentity taggedIdentity
   * @return default near-neighbor key in a list
   */
  def defaultNearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = {
    List(taggedIdentity.subscriber + ":" + taggedIdentity.subscriberRelation)
  }

  val celebrityIdentityThreshold: Int = 700

  /**
    * Identities that contain large number of accounts or identities are matched on a set of keys.
    *
    * If there is only one nearNeighborKey then nearNeighborKey must subsume matchingKey.
    *
    * @param taggedIdentity taggedIdentity
    * @return list of keys that uniquely identifies the celebrity identity
    */
  def matchingKeysForCelebrity(taggedIdentity: TaggedIdentity): List[String]

  /**
    * Returns true if two identities match.
    *
    * Equal operation must be reflexive i.e. a == b implies b == a.
    * Although, the equal operation may or may not be transitive, i.e., a == b, b == c, may not imply b == c
    *
    * @param taggedIdentity1 first Identity containing all the fields specified in the IdentifyingAttributes.
    *                        If a curatedEvent does not contain the identifyingAttributes, then the value would be ""
    *
    * @param taggedIdentity2 second Identity containing all the fields specified in the IdentifyingAttributes.
    *                        If a curatedEvent does not contain the identifyingAttributes, then the value would be ""
    *
    * @return True if match otherwise False, Match distance 0.0 (nearest) to 1.0 (farthest)
    */
  def doesMatch(taggedIdentity1: TaggedIdentity,
                taggedIdentity2: TaggedIdentity): (Boolean, Double) = {
    if (taggedIdentity1.subscriberRelation == taggedIdentity2.subscriberRelation && taggedIdentity1.subscriber == taggedIdentity2.subscriber)
      (true, 0.0)
    else (false, 1.0)
  }


  def doesMatchWithProfiling(taggedIdentity: TaggedIdentity, taggedIdentity2: TaggedIdentity): ((Boolean, Double), String) = {
    val startTime = System.nanoTime()


    val result = doesMatch(taggedIdentity, taggedIdentity2)
    val endTime = System.nanoTime()

    val duration = "" + (endTime - startTime);

    (result, duration)
  }



  /**
   * Default doesMatch for keep apart.
   *
   * If a Keep Apart (KA) identity exists in a pin, each identity in that pin is compared to each of the KA identities
   * by doesMatchKA function in IdentityMatcher class, returning a double-typed value indicating the similarity between
   * the identity and the KA identity. The larger the value, the more similar the identity is to the KA identity. The
   * KA identity with the largest similarity value means the identity is closer to this KA identity than to the others.
   * Then this identity is assigned to this KA identity group. Likewise, other identities are assigned to the KA
   * identity groups that they are closer to. In the end, the pin is split by the KA identity groups. By default, the
   * doesMatchKA function compares the identifyingAttributes values of the identity and the KA identity one by one. If
   * a pair is matched, it adds 1.0 to the similarity value. In other words, the default doesMatchKA considers all the
   * identifyingAttributes equally weighted. Alternatively, extension can implement a custom doesMatchKA function and
   * has freedom to evaluate the similarity value.
   *
   * @param taggedIdentity1 first Identity containing the fields specified in the IdentifyingAttributes.
   * @param taggedIdentity2 second Identity containing the fields specified in the IdentifyingAttributes.
   * @return a Double-typed similarity score for these two identities
   */
  def doesMatchKA(taggedIdentity1: TaggedIdentity,
                taggedIdentity2: TaggedIdentity): Double = {
    val identityAttributes = taggedIdentity1.identity.attributes.keys.toArray
    identityAttributes.map(attribute => {
      val someValue1 = taggedIdentity1.identity.attributes.get(attribute)
      val score = if (someValue1 != None) {
        val value1 = someValue1.get
        val someValue2 = taggedIdentity2.identity.attributes.get(attribute)
        if (someValue2 != None) {
          val value2 = someValue2.get
          if (value1 == value2) 1.0 else 0.0
        } else {
          0.0
        }
      } else {
        0.0
      }
      score
    })
      .reduce(_+_)
  }
}
