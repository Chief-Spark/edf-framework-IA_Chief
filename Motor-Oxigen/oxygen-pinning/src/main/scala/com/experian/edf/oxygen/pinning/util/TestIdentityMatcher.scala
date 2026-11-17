package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.domain.TaggedIdentity


class NullIdentityMatcher extends IdentityMatcher {

  /**
    * List of keys that are used for find identities that are potentially matches
    *
    * @return
    */
  override def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = super.nearNeighborKeys(taggedIdentity)

  /**
    * Returns true if two identities match.
    *
    * Equal operation must be reflexive i.e. a == b implies b == a.
    * Although, the equal operation may or may not be transitive, i.e., a == b, b == c, may not imply b == c
    *
    * @param taggedIdentity1 first Identity containing all the fields specified in the IdentifyingAttributes.
    *                        If a curatedEvent does not contain the identifyingAttributes, then the value would be ""
    * @param taggedIdentity2 second Identity containing all the fields specified in the IdentifyingAttributes.
    *                        If a curatedEvent does not contain the identifyingAttributes, then the value would be ""
    * @return True if match otherwise False, Match distance 0.0 (nearest) to 1.0 (farthest)
    */
  override def doesMatch(taggedIdentity1: TaggedIdentity, taggedIdentity2: TaggedIdentity): (Boolean, Double) = super.doesMatch(taggedIdentity1, taggedIdentity2)

  /**
    * Identities that contain large number of accounts or identities are matched on a set of keys.
    *
    * If there is only one nearNeighborKey then nearNeighborKey must subsume matchingKey.
    *
    * @param tagggedIdentity
    * @return list of keys that uniquely identifies the celebrity identity
    */
  override def matchingKeysForCelebrity(tagggedIdentity: TaggedIdentity): List[String] = super.nearNeighborKeys(tagggedIdentity)
}
