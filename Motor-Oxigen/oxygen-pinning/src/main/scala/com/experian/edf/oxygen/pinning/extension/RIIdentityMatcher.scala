package com.experian.edf.oxygen.pinning.extension

import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.pinning.util.IdentityMatcher

/**
  * Utility object that contains the keys to retrieve Identifying Attributes from TaggedIdentityEvent
  * NOTE that these key MUST be same as that are specified in the IdentifyingAttributes conf file.
  */
object RIIdentityAttributesKey {
  val firstName = "firstName"
  val middleName = "middleName"
  val firstLastName = "firstLastName"
  val secondLastName = "secondLastName"
  val nationalId = "nationalId"
}

/**
  * Implementation of Identity Matcher.
  */
class RIIdentityMatcher extends IdentityMatcher {

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
    * @return True or False
    */

  override def doesMatch(taggedIdentity1: TaggedIdentity, taggedIdentity2: TaggedIdentity): (Boolean, Double) = {

    // MUST CALL SUPER.  This will enable default matching on subscriber and subscriberRelation
    val didMatch = super.doesMatch(taggedIdentity1, taggedIdentity2)

    if (didMatch._1) (true, 0.0)
    else {
      val thisFirstName = taggedIdentity1.identity.attributes.get(RIIdentityAttributesKey.firstName).get.toString
      val thisFirstLastName = taggedIdentity1.identity.attributes.get(RIIdentityAttributesKey.firstName).get.toString
      val thisNationalId = taggedIdentity1.identity.attributes.get(RIIdentityAttributesKey.nationalId).get.toString

      val thatFirstName = taggedIdentity2.identity.attributes.get(RIIdentityAttributesKey.firstName).get.toString
      val thatFirstLastName = taggedIdentity2.identity.attributes.get(RIIdentityAttributesKey.firstName).get.toString
      val thatNationalId = taggedIdentity2.identity.attributes.get(RIIdentityAttributesKey.nationalId).get.toString

      // In this case, we match if national id is the same OR firstName and LastName are equal.
      if (thisNationalId == thatNationalId) (true, 0.0)
      else if (thisFirstName == thatFirstName && thisFirstLastName == thatFirstLastName) (true, 0.5)
      else (false, 1.0)
    }
  }

  /**
    * List of keys that are used for find identities that are potentially matches
    *
    * @param taggedIdentity
    * @return
    */
  override def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val keys = super.nearNeighborKeys(taggedIdentity)
    val additionalKeys = getKeys(taggedIdentity)
    keys ::: additionalKeys
  }

  // Takes a valid record and return all the LSH Keys
  private[this] def getKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val nationalId = taggedIdentity.identity.attributes.get(RIIdentityAttributesKey.nationalId.toString).get.toString
    val firstName = taggedIdentity.identity.attributes.get(RIIdentityAttributesKey.firstName).get.toString
    val middleName = taggedIdentity.identity.attributes.get(RIIdentityAttributesKey.middleName).get.toString
    val firstLastName = taggedIdentity.identity.attributes.get(RIIdentityAttributesKey.firstLastName).get.toString
    val secondLastName = taggedIdentity.identity.attributes.get(RIIdentityAttributesKey.secondLastName).get.toString

    List(nationalId, nationalIdLastNamePrefixKey(nationalId, firstLastName),
      nationalIdLastNameSuffixKey(nationalId, firstLastName), nationalIdLastNamePrefixKey(nationalId, secondLastName),
      nationalIdLastNameSuffixKey(nationalId, secondLastName)).filter(_ != null).distinct

  }

  private[this] def nationalIdLastNamePrefixKey(nationalId: String, lastName: String): String = {
    if (lastName == "") null
    else nationalIdPrefix(nationalId, 5) + lastName.substring(0, if (5 <= lastName.length) 5 else lastName.length)

  }

  private[this] def nationalIdLastNameSuffixKey(nationalId: String, lastName: String): String = {
    if (lastName == "") null
    else nationalIdPrefix(nationalId, 5) + lastName.substring(if (5 <= lastName.length) lastName.length-5 else 0)
  }

  private[this] def nationalIdPrefix(nationalId: String, c: Int): String = {
    nationalId.substring(0, if (5 < nationalId.length) 5 else nationalId.length)
  }

  /**
    * Identities that contain large number of accounts or identities are matched on a set of keys.
    *
    * If there is only one nearNeighborKey then nearNeighborKey must subsume matchingKey.
    *
    * @param taggedIdentity
    * @return list of keys that uniquely identifies the celebrity identity
    */
  override def matchingKeysForCelebrity(taggedIdentity: TaggedIdentity): List[String] = super.nearNeighborKeys(taggedIdentity)
}
