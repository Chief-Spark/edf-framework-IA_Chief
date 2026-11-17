package com.experian.edf.oxygen.pinning.extension

import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.pinning.extension.Constants._
import com.experian.edf.oxygen.pinning.extension.Curationconstant._
import com.experian.edf.oxygen.pinning.extension.ReceitaFederalCompany._
import com.experian.edf.oxygen.pinning.extension.ReceitaFederalIndividual._
import com.experian.edf.oxygen.pinning.util.IdentityMatcher


class IdentityImplPerson extends IdentityMatcher {

  override def doesMatch(taggedIdentity1: TaggedIdentity, taggedIdentity2: TaggedIdentity): (Boolean, Double) = {
    val cpf1 = taggedIdentity1.identity.attributes.getOrElse(NUMERO_DO_CPF, EMPTY_STRING).toString
    val cpf2 = taggedIdentity2.identity.attributes.getOrElse(NUMERO_DO_CPF, EMPTY_STRING).toString

    val cpfShort1 = taggedIdentity1.identity.attributes.getOrElse(NUMERO_DO_CPF_SHORT, EMPTY_STRING).toString
    val cpfShort2 = taggedIdentity2.identity.attributes.getOrElse(NUMERO_DO_CPF_SHORT, EMPTY_STRING).toString

    val rfCpf1 = taggedIdentity1.identity.attributes.getOrElse(ReceitaFederalIndividual.NU_DOCUMENTO_CAD, EMPTY_STRING).toString
    val rfCpf2 = taggedIdentity2.identity.attributes.getOrElse(ReceitaFederalIndividual.NU_DOCUMENTO_CAD, EMPTY_STRING).toString

    val partnerName1 = taggedIdentity1.identity.attributes.getOrElse(R04_NOME_DA_PESSOA_CURATED, EMPTY_STRING).toString
    val partnerName2 = taggedIdentity2.identity.attributes.getOrElse(R04_NOME_DA_PESSOA_CURATED, EMPTY_STRING).toString

    val rfName1 = taggedIdentity1.identity.attributes.getOrElse(ReceitaFederalIndividual.NO_COMPLETO_CAD, EMPTY_STRING).toString
    val rfName2 = taggedIdentity2.identity.attributes.getOrElse(ReceitaFederalIndividual.NO_COMPLETO_CAD, EMPTY_STRING).toString

    val didMatch = super.doesMatch(taggedIdentity1, taggedIdentity2)

    if (didMatch._1) (true, 1.0)
    else if (cpfShort1.nonEmpty && rfCpf1 == cpfShort2) (true, 1.0)
    else if (cpfShort2.nonEmpty && cpfShort1 == rfCpf2) (true, 1.0)
    else if (partnerName1.nonEmpty && partnerName1 == rfName2) (true, 1.0)
    else if (partnerName2.nonEmpty && rfName1 == partnerName2) (true, 1.0)
    else (false, 0.0)
  }

  override def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val keys = super.nearNeighborKeys(taggedIdentity)
    val additionalKeys = getKeys(taggedIdentity)
    keys ::: additionalKeys
  }

  private[this] def getKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val cpf = taggedIdentity.identity.attributes.getOrElse(NUMERO_DO_CPF, EMPTY_STRING).toString
    val cpfShort = taggedIdentity.identity.attributes.getOrElse(NUMERO_DO_CPF_SHORT, EMPTY_STRING).toString
    val rfCpf = taggedIdentity.identity.attributes.getOrElse(ReceitaFederalIndividual.NU_DOCUMENTO_CAD, EMPTY_STRING).toString
    val partnerName = taggedIdentity.identity.attributes.getOrElse(R04_NOME_DA_PESSOA_CURATED, EMPTY_STRING).toString

    val rfName = taggedIdentity.identity.attributes.getOrElse(ReceitaFederalIndividual.NO_COMPLETO_CAD, EMPTY_STRING).toString

    val key = List(cpfShort, rfCpf, partnerName, rfName)

    key
      .filter(x => if (!x.isEmpty) true else false)
      .distinct
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
