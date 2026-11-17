package com.experian.edf.oxygen.curation.extension.event

import com.experian.edf.oxygen.curation.extension.service.infotypes.FilingOffice
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Frequency}
import com.google.gson.Gson

case class CreditCuratedEventStructured(
                                         val idType : Int,
                                         val nationalId : String,
                                         val accountNumber : String,
                                         val firstName : String,
                                         val middleName : String,
                                         val firstLastName : String,
                                         val secondLastName : String,
                                         val situationOfHolder : Int,
                                         val openningDate : Int,
                                         val dueDate : Int,
                                         val responsibleOrQualityOfDebtor : Int,
                                         val typeOfobligation : Int,
                                         val mortgageSubsidy : Int,
                                         val dateOfSubsidy : Int,
                                         val endOfThatContractCreatesTheObligation : Int,
                                         val wayToPay : Int,
                                         val frequencyOfPayment : Int,
                                         val novelty : Int,
                                         val stateOfOriginAccount : Int,
                                         val stateDateOrigin : Int,
                                         val stateOfMind : Int,
                                         val dateStatusAccount : Int,
                                         val stateOfPlastics : Int,
                                         val dateStatusPlastic : Int,
                                         val adjective : Int,
                                         val adjDate : Int,
                                         val classCard : Int,
                                         val franchise : Int,
                                         val brandNamePrivate : String,
                                         val typeOfCurrency : Int,
                                         val typeOfWarranty : Int,
                                         val qualification : String,
                                         val probabilityBreach : Int,
                                         val ageOfMora : Int,
                                         val creditLimit : Long,
                                         val balance : Long,
                                         val availableValue : Long,
                                         val valueMonthly : Long,
                                         val balanceValueInMora : Long,
                                         val totalFees : Int,
                                         val contributionsCancelled : Int,
                                         val arrears : Int,
                                         val clausePermanence : Int,
                                         val dateClausePermanence : Int,
                                         val paydayLimit : Int,
                                         val paymentDate : Int,
                                         val officeOfFiling : FilingOffice,
                                         val cityOfFiling : String,
                                         val daneCityCodeFiling : Int,
                                         val cityOfResidenceOrCommercialCity : String,
                                         val daneCityCodeResidenceOrDaneCityCodeCommercial : Int,
                                         val departmentResidenceOrDepartmentCommercial : String,
                                         val directionOfResidenceOrCommercialAddress : String,
                                         val phoneResidenceOrBusinessPhone : Long,
                                         val cityLabor : String,
                                         val daneCityCodeLabor : Int,
                                         val departmentLabor : String,
                                         val workAddress : String,
                                         val phoneWork : Int,
                                         val cityOfCorrespondence : String,
                                         val daneCityCodeCorrespondence : Int,
                                         val departmentOfCorrespondence : String,
                                         val directionOfCorrespondence : String,
                                         val email : String,
                                         val cellPhone : Long,
                                         val subscriberDestination : Int,
                                         val cardNumber : Long,
                                         val detailWarranty : String,
                                         val isNewSubscriberRelation: Boolean = false,
                                         val refersToRelation: String = "",
                                         val endMostMessage: Boolean,
                                         val recNewFrequency: Frequency) {

}

class CreditCuratedEventJson(curatedRecord: String) extends CuratedEvent(curatedRecord) {

  lazy private val gson = new Gson()
  val creditCuratedEventStructured = gson.fromJson(curatedRecord, classOf[CreditCuratedEventStructured])

  override def read(curatedRecord: String): CuratedEvent = {
    new CreditCuratedEventJson(creditCuratedEventStructured)
  }

  override def write: String = {
    gson.toJson(creditCuratedEventStructured)
  }

  def this(creditCuratedEventStructured: CreditCuratedEventStructured) = {
    this((new Gson()).toJson(creditCuratedEventStructured))
  }
}