package com.experian.edf.oxygen.curation.extension.event

import com.experian.edf.oxygen.domain.internal.CuratedEvent


/**
  * CreditCuratedEvent.
  *
  * It must a constructor to construct the event from a curated string.  This
  * constructor is used by the framework to construct a curatedEvent.
  *
  * Optionally, it should provide a constructor to create a curatedEvent by all its fields.  It will be
  * used by the processor to construct the curatedEvent after curating a acceptedEvent.
  *
  * It is up to the implementation to decide what kind of structure a curated even is going to have.
  * Here we implement curated event as a ":::" separated string.  We split the curated string on ":::"
  * to get the appropriate fields.
  *
  * One can choose to use a JSON structure and use a JSON parser to parse the JSON string into a curated event.
  * Or, alternatively, one can choose a fixed length string for a curatedRecord and provide methods to
  * access thos fields.
  *
  * @param curatedRecord  is a string that is read by the framework and use to create the CuratedEvent.
  */

class CreditCuratedEvent(curatedRecord: String) extends CuratedEvent(curatedRecord) {
  override def write: String = toString

  override def read(curatedRec: String): CuratedEvent = {
    new CreditCuratedEvent(curatedRec)
  }

  private val IdTypePosition = 0
  private val NationalIdPosition = 1
  private val AccountNumberPosition = 2
  private val FirstNamePosition = 3
  private val MiddleNamePosition = 4
  private val FirstLastNamePosition = 5
  private val SecondLastNamePosition = 6
  private val SituationOfHolderPosition = 7
  private val OpenningDatePosition = 8
  private val DueDatePosition = 9
  private val ResponsibleOrQualityOfDebtorPosition = 10
  private val TypeOfobligationPosition = 11
  private val MortgageSubsidyPosition = 12
  private val DateOfSubsidyPosition = 13
  private val EndOfThatContractCreatesTheObligationPosition = 14
  private val WayToPayPosition = 15
  private val FrequencyOfPaymentPosition = 16
  private val NoveltyPosition = 17
  private val StateOfOriginAccountPosition = 18
  private val StateDateOriginPosition = 19
  private val StateOfMindPosition = 20
  private val DateStatusAccountPosition = 21
  private val StateOfPlasticsPosition = 22
  private val DateStatusPlasticPosition = 23
  private val AdjectivePosition = 24
  private val AdjDatePosition = 25
  private val ClassCardPosition = 26
  private val FranchisePosition = 27
  private val BrandNamePrivatePosition = 28
  private val TypeOfCurrencyPosition = 29
  private val TypeOfWarrantyPosition = 30
  private val QualificationPosition = 31
  private val ProbabilityBreachPosition = 32
  private val AgeOfMoraPosition = 33
  private val InitialValuePosition = 34
  private val DebtBalancePosition = 35
  private val AvailableValuePosition = 36
  private val ValueMonthlyPosition = 37
  private val BalanceValueInMoraPosition = 38
  private val TotalFeesPosition = 39
  private val ContributionsCancelledPosition = 40
  private val ArrearsPosition = 41
  private val ClausePermanencePosition = 42
  private val DateClausePermanencePosition = 43
  private val PaydayLimitPosition = 44
  private val PaymentDatePosition = 45
  private val OfficeOfFilingPosition = 46
  private val CityOfFilingPosition = 47
  private val DaneCityCodeFilingPosition = 48
  private val CityOfResidenceOrCommercialCityPosition = 49
  private val DaneCityCodeResidenceOrDaneCityCodeCommercialPosition = 50
  private val DepartmentResidenceOrDepartmentCommercialPosition = 51
  private val DirectionOfResidenceOrCommercialAddressPosition = 52
  private val PhoneResidenceOrBusinessPhonePosition = 53
  private val CityLaborPosition = 54
  private val DaneCityCodeLaborPosition = 55
  private val DepartmentLaborPosition = 56
  private val WorkAddressPosition = 57
  private val PhoneWorkPosition = 58
  private val CityOfCorrespondencePosition = 59
  private val DaneCityCodeCorrespondencePosition = 60
  private val DepartmentOfCorrespondencePosition = 61
  private val DirectionOfCorrespondencePosition = 62
  private val EmailPosition = 63
  private val CellPhonePosition = 64
  private val SubscriberDestinationPosition = 65
  private val CardNumberPosition = 66
  private val DetailWarrantyPosition = 67

  val parts = curatedRecord.split(":::", -1)
  val idType = parts(IdTypePosition).toInt
  val nationalId = parts(NationalIdPosition)
  val accountNumber = parts(AccountNumberPosition)
  val firstName = parts(FirstNamePosition)
  val middleName = parts(MiddleNamePosition)
  val firstLastName = parts(FirstLastNamePosition)
  val secondLastName = parts(SecondLastNamePosition)
  val situationOfHolder = parts(SituationOfHolderPosition).toInt
  val openningDate = parts(OpenningDatePosition).toInt
  val dueDate = parts(DueDatePosition).toInt
  val responsibleOrQualityOfDebtor = parts(ResponsibleOrQualityOfDebtorPosition).toInt
  val typeOfobligation = parts(TypeOfobligationPosition).toInt
  val mortgageSubsidy = parts(MortgageSubsidyPosition).toInt
  val dateOfSubsidy = parts(DateOfSubsidyPosition).toInt
  val endOfThatContractCreatesTheObligation = parts(EndOfThatContractCreatesTheObligationPosition).toInt
  val wayToPay = parts(WayToPayPosition).toInt
  val frequencyOfPayment = parts(FrequencyOfPaymentPosition).toInt
  val novelty = parts(NoveltyPosition).toInt
  val stateOfOriginAccount = parts(StateOfOriginAccountPosition).toInt
  val stateDateOrigin = parts(StateDateOriginPosition).toInt
  val stateOfMind = parts(StateOfMindPosition).toInt
  val dateStatusAccount = parts(DateStatusAccountPosition).toInt
  val stateOfPlastics = parts(StateOfPlasticsPosition).toInt
  val dateStatusPlastic = parts(DateStatusPlasticPosition).toInt
  val adjective = parts(AdjectivePosition).toInt
  val adjDate = parts(AdjDatePosition).toInt
  val classCard = parts(ClassCardPosition).toInt
  val franchise = parts(FranchisePosition).toInt
  val brandNamePrivate = parts(BrandNamePrivatePosition)
  val typeOfCurrency = parts(TypeOfCurrencyPosition).toInt
  val typeOfWarranty = parts(TypeOfWarrantyPosition).toInt
  val qualification = parts(QualificationPosition)
  val probabilityBreach = parts(ProbabilityBreachPosition).toInt
  val ageOfMora = parts(AgeOfMoraPosition).toInt
  val initialValue = parts(InitialValuePosition).toLong
  val debtBalance = parts(DebtBalancePosition).toLong
  val availableValue = parts(AvailableValuePosition).toLong
  val valueMonthly = parts(ValueMonthlyPosition).toLong
  val balanceValueInMora = parts(BalanceValueInMoraPosition).toLong
  val totalFees = parts(TotalFeesPosition).toInt
  val contributionsCancelled = parts(ContributionsCancelledPosition).toInt
  val arrears = parts(ArrearsPosition).toInt
  val clausePermanence = parts(ClausePermanencePosition).toInt
  val dateClausePermanence = parts(DateClausePermanencePosition).toInt
  val paydayLimit = parts(PaydayLimitPosition).toInt
  val paymentDate = parts(PaymentDatePosition).toInt
  val officeOfFiling = parts(OfficeOfFilingPosition)
  val cityOfFiling = parts(CityOfFilingPosition)
  val daneCityCodeFiling = parts(DaneCityCodeFilingPosition).toInt
  val cityOfResidenceOrCommercialCity = parts(CityOfResidenceOrCommercialCityPosition)
  val daneCityCodeResidenceOrDaneCityCodeCommercial = parts(DaneCityCodeResidenceOrDaneCityCodeCommercialPosition).toInt
  val departmentResidenceOrDepartmentCommercial = parts(DepartmentResidenceOrDepartmentCommercialPosition)
  val directionOfResidenceOrCommercialAddress = parts(DirectionOfResidenceOrCommercialAddressPosition)
  val phoneResidenceOrBusinessPhone = parts(PhoneResidenceOrBusinessPhonePosition).toLong
  val cityLabor = parts(CityLaborPosition)
  val daneCityCodeLabor = parts(DaneCityCodeLaborPosition).toInt
  val departmentLabor = parts(DepartmentLaborPosition)
  val workAddress = parts(WorkAddressPosition)
  val phoneWork = parts(PhoneWorkPosition).toInt
  val cityOfCorrespondence = parts(CityOfCorrespondencePosition)
  val daneCityCodeCorrespondence = parts(DaneCityCodeCorrespondencePosition).toInt
  val departmentOfCorrespondence = parts(DepartmentOfCorrespondencePosition)
  val directionOfCorrespondence = parts(DirectionOfCorrespondencePosition)
  val email = parts(EmailPosition)
  val cellPhone = parts(CellPhonePosition).toLong
  val subscriberDestination = parts(SubscriberDestinationPosition).toInt
  val cardNumber = parts(CardNumberPosition).toLong
  val detailWarranty = parts(DetailWarrantyPosition)

  def this(
            idType: Int,
            nationalId: String,
            accountNumber: String,
            firstName: String,
            middleName: String,
            firstLastName: String,
            secondLastName: String,
            situationOfHolder: Int,
            openningDate: Int,
            dueDate: Option[Int],
            responsibleOrQualityOfDebtor: Int,
            typeOfobligation: Int,
            mortgageSubsidy: Int,
            dateOfSubsidy: Int,
            endOfThatContractCreatesTheObligation: Int,
            wayToPay: Int,
            frequencyOfPayment: Int,
            novelty: Int,
            stateOfOriginAccount: Int,
            stateDateOrigin: Int,
            stateOfMind: Int,
            dateStatusAccount: Int,
            stateOfPlastics: Int,
            dateStatusPlastic: Int,
            adjective: Int,
            adjDate: Int,
            classCard: Int,
            franchise: Int,
            brandNamePrivate: String,
            typeOfCurrency: Int,
            typeOfWarranty: Int,
            qualification: String,
            probabilityBreach: Int,
            ageOfMora: Int,
            initialValue: Long,
            debtBalance: Long,
            availableValue: Long,
            valueMonthly: Long,
            balanceValueInMora: Long,
            totalFees: Int,
            contributionsCancelled: Int,
            arrears: Int,
            clausePermanence: Int,
            dateClausePermanence: Int,
            paydayLimit: Int,
            paymentDate: Int,
            officeOfFiling: String,
            cityOfFiling: String,
            daneCityCodeFiling: Int,
            cityOfResidenceOrCommercialCity: String,
            daneCityCodeResidenceOrDaneCityCodeCommercial: Int,
            departmentResidenceOrDepartmentCommercial: String,
            directionOfResidenceOrCommercialAddress: String,
            phoneResidenceOrBusinessPhone: Long,
            cityLabor: String,
            daneCityCodeLabor: Int,
            departmentLabor: String,
            workAddress: String,
            phoneWork: Int,
            cityOfCorrespondence: String,
            daneCityCodeCorrespondence: Int,
            departmentOfCorrespondence: String,
            directionOfCorrespondence: String,
            email: String,
            cellPhone: Long,
            subscriberDestination: Int,
            cardNumber: Long,
            detailWarranty: String
          ) {
    this(idType + ":::" + nationalId + ":::" + accountNumber + ":::" + firstName +
      ":::" + middleName + ":::" + firstLastName + ":::" + secondLastName +
      ":::" + situationOfHolder + ":::" + openningDate + ":::" + dueDate + ":::" + responsibleOrQualityOfDebtor +
      ":::" + typeOfobligation + ":::" + mortgageSubsidy + ":::" + dateOfSubsidy +
      ":::" + endOfThatContractCreatesTheObligation + ":::" + wayToPay + ":::" + frequencyOfPayment +
      ":::" + novelty + ":::" + stateOfOriginAccount + ":::" + stateDateOrigin + ":::" + stateOfMind +
      ":::" +dateStatusAccount + ":::" + stateOfPlastics + ":::" + dateStatusPlastic + ":::" + adjective +
      ":::" + adjDate + ":::" + classCard + ":::" + franchise + ":::" + brandNamePrivate + ":::" + typeOfCurrency +
      ":::" + typeOfWarranty + ":::" + qualification + ":::" + probabilityBreach + ":::" + ageOfMora +
      ":::" + initialValue + ":::" + debtBalance + ":::" + availableValue + ":::" + valueMonthly +
      ":::" + balanceValueInMora + ":::" + totalFees + ":::" + contributionsCancelled + ":::" + arrears +
      ":::" + clausePermanence + ":::" + dateClausePermanence + ":::" + paydayLimit + ":::" + paymentDate +
      ":::" + officeOfFiling + ":::" + cityOfFiling + ":::" + daneCityCodeFiling +
      ":::" + cityOfResidenceOrCommercialCity + ":::" + daneCityCodeResidenceOrDaneCityCodeCommercial +
      ":::" + departmentResidenceOrDepartmentCommercial + ":::" + directionOfResidenceOrCommercialAddress +
      ":::" + phoneResidenceOrBusinessPhone + ":::" + cityLabor + ":::" + daneCityCodeLabor +
      ":::" + departmentLabor + ":::" + workAddress + ":::" + phoneWork + ":::" + cityOfCorrespondence +
      ":::" + daneCityCodeCorrespondence + ":::" + departmentOfCorrespondence +
      ":::" + directionOfCorrespondence + ":::" + email + ":::" + cellPhone +
      ":::" + subscriberDestination + ":::" + cardNumber + ":::" + detailWarranty)
  }
}
