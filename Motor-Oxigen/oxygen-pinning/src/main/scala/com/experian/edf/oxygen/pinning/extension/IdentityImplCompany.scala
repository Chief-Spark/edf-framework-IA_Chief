package com.experian.edf.oxygen.pinning.extension

import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.pinning.extension.Constants._
import com.experian.edf.oxygen.pinning.extension.Curationconstant._
import com.experian.edf.oxygen.pinning.extension.ReceitaFederalCompany._
import com.experian.edf.oxygen.pinning.extension.ReceitaFederalIndividual._
import com.experian.edf.oxygen.pinning.util.IdentityMatcher


class IdentityImplCompany extends IdentityMatcher {

  override def doesMatch(taggedIdentity1: TaggedIdentity, taggedIdentity2: TaggedIdentity): (Boolean, Double) = {
    /**
      * To join R01,R02,R03,R05,R06,R17 and R18 using Parent Nire.
      */
    val nire1 = taggedIdentity1.identity.attributes.getOrElse(NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL, EMPTY_STRING).toString
    val nire2 = taggedIdentity2.identity.attributes.getOrElse(NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL, EMPTY_STRING).toString

    /**
      * To join R15 with R03 parent company.
      */
    val nirePartner1 = taggedIdentity1.identity.attributes.getOrElse(NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL_PARTNER, EMPTY_STRING).toString
    val nirePartner2 = taggedIdentity2.identity.attributes.getOrElse(NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL_PARTNER, EMPTY_STRING).toString

    /**
      * When CNPJ/CPF is not match then join Receita Federal and RecordType with Constitution date.
      */
    val date1 = taggedIdentity1.identity.attributes.getOrElse(DATA_DA_CONSTITUICAO, EMPTY_STRING).toString
    val date2 = taggedIdentity2.identity.attributes.getOrElse(DATA_DA_CONSTITUICAO, EMPTY_STRING).toString

    val rf_date1 = taggedIdentity1.identity.attributes.getOrElse(DT_ABERTURA_CAD, EMPTY_STRING).toString
    val rf_date2 = taggedIdentity2.identity.attributes.getOrElse(DT_ABERTURA_CAD, EMPTY_STRING).toString

    val rf_name1 = taggedIdentity1.identity.attributes.getOrElse(NO_COMPLETO_CAD, EMPTY_STRING).toString
    val rf_name2 = taggedIdentity2.identity.attributes.getOrElse(NO_COMPLETO_CAD, EMPTY_STRING).toString

    val name1 = taggedIdentity1.identity.attributes.getOrElse(NOME_DA_EMPRESA, EMPTY_STRING).toString
    val name2 = taggedIdentity2.identity.attributes.getOrElse(NOME_DA_EMPRESA, EMPTY_STRING).toString


    val cnpjShort1 = taggedIdentity1.identity.attributes.getOrElse(NUMERO_DO_CNPJ_SHORT, EMPTY_STRING).toString
    val cnpjShort2 = taggedIdentity2.identity.attributes.getOrElse(NUMERO_DO_CNPJ_SHORT, EMPTY_STRING).toString

    val rf_cnpj1 = taggedIdentity1.identity.attributes.getOrElse(ReceitaFederalCompany.NU_DOCUMENTO_CAD, EMPTY_STRING).toString
    val rf_cnpj2 = taggedIdentity2.identity.attributes.getOrElse(ReceitaFederalCompany.NU_DOCUMENTO_CAD, EMPTY_STRING).toString

    val csc1 = taggedIdentity1.identity.attributes.getOrElse(CODIGO_DA_SEQUENCIA_DA_EMPRESA, EMPTY_STRING).toString
    val csc2 = taggedIdentity2.identity.attributes.getOrElse(CODIGO_DA_SEQUENCIA_DA_EMPRESA, EMPTY_STRING).toString
    val didMatch = super.doesMatch(taggedIdentity1, taggedIdentity2)

    if (didMatch._1) (true, 1.0)
    else if (nire1.nonEmpty && nire1 == nire2) (true, 1.0)

    else if (nirePartner1.nonEmpty && nirePartner1 == nire2) (true, 1.0)
    else if (nirePartner2.nonEmpty && nire1 == nirePartner2) (true, 1.0)

    // join receita federal company data with company data
    else if (rf_cnpj1.nonEmpty && rf_cnpj1 == cnpjShort2) (true, 1.0)
    else if (rf_cnpj2.nonEmpty && cnpjShort1 == rf_cnpj2) (true, 1.0)

    else if (rf_date1.nonEmpty && rf_date1 == date2) (true, 1.0)
    else if (rf_date2.nonEmpty && rf_date2 == date1) (true, 1.0)

    else if (rf_name1.nonEmpty && rf_name1 == name2) (true, 1.0)
    else if (rf_name2.nonEmpty && rf_name2 == name1) (true, 1.0)

    else if (csc1.nonEmpty && csc1 == csc2) (true, 1.0)
    else (false, 0.0)
  }

  override def nearNeighborKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val keys = super.nearNeighborKeys(taggedIdentity)
    val additionalKeys = getKeys(taggedIdentity)
    keys ::: additionalKeys
  }

  private[this] def getKeys(taggedIdentity: TaggedIdentity): List[String] = {
    val recordType = taggedIdentity.identity.attributes.getOrElse(RECORD_TYPE_CURATED, EMPTY_STRING).toString
    val nire = taggedIdentity.identity.attributes.getOrElse(NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL, EMPTY_STRING).toString

    val nirePartner = taggedIdentity.identity.attributes.getOrElse(NUMERO_DA_INSCRICAO_NA_JUNTA_COMERCIAL_PARTNER, EMPTY_STRING).toString

    // TODO : add company name and make key joint key date + name not only for record type 05 it should cover 01 also
    val date = taggedIdentity.identity.attributes.getOrElse(DATA_DA_CONSTITUICAO, EMPTY_STRING).toString

    val rf_Date = taggedIdentity.identity.attributes.getOrElse(DT_ABERTURA_CAD, EMPTY_STRING).toString

    val name = taggedIdentity.identity.attributes.getOrElse(NOME_DA_EMPRESA, EMPTY_STRING).toString

    val rf_Name = taggedIdentity.identity.attributes.getOrElse(NO_COMPLETO_CAD, EMPTY_STRING).toString

    val csc = taggedIdentity.identity.attributes.getOrElse(CODIGO_DA_SEQUENCIA_DA_EMPRESA, EMPTY_STRING).toString

    val rfCnpj = taggedIdentity.identity.attributes.getOrElse(ReceitaFederalCompany.NU_DOCUMENTO_CAD, EMPTY_STRING).toString

    val cnpjShort = taggedIdentity.identity.attributes.getOrElse(NUMERO_DO_CNPJ_SHORT, EMPTY_STRING).toString

    val key = List(nire, nirePartner, csc, rfCnpj, cnpjShort, date, rf_Date, name, rf_Name)

    key.filter(x => if (!x.isEmpty) true else false)
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

