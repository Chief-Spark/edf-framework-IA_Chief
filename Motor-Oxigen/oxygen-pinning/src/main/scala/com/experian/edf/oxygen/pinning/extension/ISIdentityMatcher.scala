package com.experian.edf.oxygen.pinning.extension

import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.pinning.util.IdentityMatcher

/**
  * Utility object that contains the keys to retrieve Identifying Attributes from TaggedIdentityEvent
  * NOTE that these key MUST be same as that are specified in the IdentifyingAttributes conf file.
  */
object ISIdentityAttributesKey {
  val firstName = "firstName"
  val middleName = "middleName"
  val firstLastName = "firstLastName"
  val secondLastName = "secondLastName"
  val nationalId = "nationalId"
  val cellPhone = "cellPhone"
}

/**
  * Implementation of Identity Matcher.
  */
class ISIdentityMatcher extends IdentityMatcher {

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
      val thisFirstName = taggedIdentity1.identity.attributes.get(ISIdentityAttributesKey.firstName).get.toString
      val thisFirstLastName = taggedIdentity1.identity.attributes.get(ISIdentityAttributesKey.firstName).get.toString
      val thisNationalId = taggedIdentity1.identity.attributes.get(ISIdentityAttributesKey.nationalId).get.toString
      val thisCellphone = taggedIdentity1.identity.attributes.get(ISIdentityAttributesKey.cellPhone).get.toString

      val thatFirstName = taggedIdentity2.identity.attributes.get(ISIdentityAttributesKey.firstName).get.toString
      val thatFirstLastName = taggedIdentity2.identity.attributes.get(ISIdentityAttributesKey.firstName).get.toString
      val thatNationalId = taggedIdentity2.identity.attributes.get(ISIdentityAttributesKey.nationalId).get.toString
      val thatCellphone = taggedIdentity2.identity.attributes.get(ISIdentityAttributesKey.cellPhone).get.toString

      val similarityThreshold = 0.6
      var similarity = 0.0
      if (thisNationalId == thatNationalId) similarity += .5
      if (thisFirstName == thatFirstName && thisFirstLastName == thatFirstLastName) similarity += .3
      if (thisCellphone == thatCellphone) similarity += .3
      val sameIdentity = if (similarity >= similarityThreshold) true else false
      (sameIdentity, similarity)
    }
  }

  /**
    * List of keys that are used for find identities that are potentially matches
    *
    * @param taggedIdentity
    * @return
    */
  override def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = {
    getKeys(taggedIdentity)
  }

  // Takes a valid record and return all the LSH Keys
  private[this] def getKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val nationalId = taggedIdentity.identity.attributes.get(ISIdentityAttributesKey.nationalId.toString).get.toString
    val firstName = taggedIdentity.identity.attributes.get(ISIdentityAttributesKey.firstName).get.toString
    val middleName = taggedIdentity.identity.attributes.get(ISIdentityAttributesKey.middleName).get.toString
    val firstLastName = taggedIdentity.identity.attributes.get(ISIdentityAttributesKey.firstLastName).get.toString
    val secondLastName = taggedIdentity.identity.attributes.get(ISIdentityAttributesKey.secondLastName).get.toString
    val cellPhone = taggedIdentity.identity.attributes.get(ISIdentityAttributesKey.cellPhone.toString).get.toString

    List(nationalId, firstNamePrefixLastNamePrefixKey(firstName, firstLastName), cellPhone).filter(_ != null).distinct

  }

  private[this] def firstNamePrefixLastNamePrefixKey(firstName: String, lastName: String): String = {
    if ((firstName == "") | (lastName == "")) null
    else firstName.substring(0, if (5 <= firstName.length) 5 else firstName.length) + lastName.substring(0, if (5 <= lastName.length) 5 else lastName.length)

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
