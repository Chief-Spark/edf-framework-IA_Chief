package com.experian.edf.oxygen.curation.extension.validation

import com.experian.edf.oxygen.curation.domain.ValidatedEvent
import com.google.gson.Gson
import org.slf4j.LoggerFactory.getLogger

class CreditEvent(
                               val idType: Int,
                               val nationalId: String,
                               val accountNumber: String,
                               val fullName: String,
                               val situationOfHolder: Int,
                               val openningDate: Int,
                               val dueDate: Int,
                               val responsibleOrQualityOfDebtor: Int,
                               val typeOfobligation: Int,
                               val mortgageSubsidy: Int,
                               val dateOfSubsidy: Int,
                               val endOfThatContractCreatesTheObligation: Int,
                               val wayToPay: Int,
                               val frequencyOfPayment: Int,
                               val novelty: Int,
                               val stateOfOriginAccount: Int,
                               val stateDateOrigin: Int,
                               val stateOfMind: Int,
                               val dateStatusAccount: Int,
                               val stateOfPlastics: Int,
                               val dateStatusPlastic: Int,
                               val adjective: Int,
                               val adjDate: Int,
                               val classCard: Int,
                               val franchise: Int,
                               val brandNamePrivate: String,
                               val typeOfCurrency: Int,
                               val typeOfWarranty: Int,
                               val qualification: String,
                               val probabilityBreach: Int,
                               val ageOfMora: Int,
                               val initialValue: Long,
                               val debtBalance: Long,
                               val availableValue: Long,
                               val valueMonthly: Long,
                               val balanceValueInMora: Long,
                               val totalFees: Int,
                               val contributionsCancelled: Int,
                               val arrears: Int,
                               val clausePermanence: Int,
                               val dateClausePermanence: Int,
                               val paydayLimit: Int,
                               val paymentDate: Int,
                               val officeOfFiling: String,
                               val cityOfFiling: String,
                               val daneCityCodeFiling: Int,
                               val cityOfResidenceOrCommercialCity: String,
                               val daneCityCodeResidenceOrDaneCityCodeCommercial: Int,
                               val departmentResidenceOrDepartmentCommercial: String,
                               val directionOfResidenceOrCommercialAddress: String,
                               val phoneResidenceOrBusinessPhone: Long,
                               val cityLabor: String,
                               val daneCityCodeLabor: Int,
                               val departmentLabor: String,
                               val workAddress: String,
                               val phoneWork: Int,
                               val cityOfCorrespondence: String,
                               val daneCityCodeCorrespondence: Int,
                               val departmentOfCorrespondence: String,
                               val directionOfCorrespondence: String,
                               val email: String,
                               val cellPhone: Long,
                               val subscriberDestination: Int,
                               val cardNumber: Long,
                               val detailWarranty: String,
                               val endMostMessage: Boolean) {}

class CreditAcceptedEventJson(rec: String) extends ValidatedEvent(rec) {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val gson = new Gson()

  override def read(rawRecord: String): CreditAcceptedEventJson = new CreditAcceptedEventJson(rawRecord)

  override def write: String = gson.toJson(this)

  val creditEvent = gson.fromJson(rec, classOf[CreditEvent])


}
