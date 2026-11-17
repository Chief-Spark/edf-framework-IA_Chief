package com.experian.edf.oxygen.curation.extension.processor

import com.experian.edf.oxygen.curation.extension.util.Util
import com.experian.edf.oxygen.curation.engines.CurationProcessor
import com.experian.edf.oxygen.curation.extension.service.CurationRIService
import com.experian.edf.oxygen.curation.extension.event.{CreditAcceptedEvent, CreditAcceptedEventJson, CreditCuratedEventJson, CreditCuratedEventStructured}
import com.experian.edf.oxygen.curation.extension.service.infotypes.SpanishName
import com.experian.edf.oxygen.curation.domain.ValidatedEvent
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Frequency, Payload}
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import org.slf4j.LoggerFactory.getLogger

class CreditCurationProcessorJson(context: CurationProcessorContext) extends CurationProcessor(context: CurationProcessorContext) {
  @transient lazy val logger = getLogger(this.getClass)

  /**
    * Implementation specific method: To create quality scored event
    *
    * @param acceptedRecord AcceptedEvent:
    * @return AcceptedEvent
    */
  override def readAcceptedEvent(acceptedRecord: String): ValidatedEvent = {
    new CreditAcceptedEvent(acceptedRecord)
  }

  /**
    * Implementation specific method: To curate the event and return a curated event
    *
    * @param taggedValidatedEvent TaggedAcceptedEvent : The tagged accepted event which needs to be curated
    * @return CuratedEvent
    */
  override def curateEvent(taggedValidatedEvent: TaggedValidatedEvent): CuratedEvent = {

    val event = new CreditAcceptedEventJson(taggedValidatedEvent.validatedRecordJson).creditEvent
    val oxygenLookupServ = context.lookup
    val lookupRetValue = Util.getLookup("consistencyfiltertable1", Map("errorcode" -> "errorCode1"),"errorvalue", oxygenLookupServ )
    logger.error(s"*****************" + lookupRetValue.head)

    val idType = event.idType
    val nationalId = event.nationalId
    val fullName = event.fullName
    val accountNumber = event.accountNumber
    val situationOfHolder = event.situationOfHolder
    val openningDate = event.openningDate
    val dueDate = event.dueDate
    val responsibleOrQualityOfDebtor = event.responsibleOrQualityOfDebtor
    val typeOfobligation = event.typeOfobligation
    val mortgageSubsidy = event.mortgageSubsidy
    val dateOfSubsidy = event.dateOfSubsidy
    val endOfThatContractCreatesTheObligation = event.endOfThatContractCreatesTheObligation
    val wayToPay = event.wayToPay
    val frequencyOfPayment = event.frequencyOfPayment
    val novelty = event.novelty
    val stateOfOriginAccount = event.stateOfOriginAccount
    val stateDateOrigin = event.stateDateOrigin
    val stateOfMind = event.stateOfMind
    val dateStatusAccount = event.dateStatusAccount
    val stateOfPlastics = event.stateOfPlastics
    val dateStatusPlastic = event.dateStatusPlastic
    val adjective = event.adjective
    val adjDate = event.adjDate
    val classCard = event.classCard
    val franchise = event.franchise
    val brandNamePrivate = event.brandNamePrivate
    val typeOfCurrency = event.typeOfCurrency
    val typeOfWarranty = event.typeOfWarranty
    val qualification = event.qualification
    val probabilityBreach = event.probabilityBreach
    val ageOfMora = event.ageOfMora
    val creditLimit = event.initialValue
    val balance = event.debtBalance
    val availableValue = event.availableValue
    val valueMonthly = event.valueMonthly
    val balanceValueInMora = event.balanceValueInMora
    val totalFees = event.totalFees
    val contributionsCancelled = event.contributionsCancelled
    val arrears = event.arrears
    val clausePermanence = event.clausePermanence
    val dateClausePermanence = event.dateClausePermanence
    val paydayLimit = event.paydayLimit
    val paymentDate = event.paymentDate
    val officeOfFiling = event.officeOfFiling
    val cityOfFiling = event.cityOfFiling
    val daneCityCodeFiling = event.daneCityCodeFiling
    val cityOfResidenceOrCommercialCity = event.cityOfResidenceOrCommercialCity
    val daneCityCodeResidenceOrDaneCityCodeCommercial = event.daneCityCodeResidenceOrDaneCityCodeCommercial
    val departmentResidenceOrDepartmentCommercial = event.departmentResidenceOrDepartmentCommercial
    val directionOfResidenceOrCommercialAddress = event.directionOfResidenceOrCommercialAddress
    val phoneResidenceOrBusinessPhone = event.phoneResidenceOrBusinessPhone
    val cityLabor = event.cityLabor
    val daneCityCodeLabor = event.daneCityCodeLabor
    val departmentLabor = event.departmentLabor
    val workAddress = event.workAddress
    val phoneWork = event.phoneWork
    val cityOfCorrespondence = event.cityOfCorrespondence
    val daneCityCodeCorrespondence = event.daneCityCodeCorrespondence
    val departmentOfCorrespondence = event.departmentOfCorrespondence
    val directionOfCorrespondence = event.directionOfCorrespondence
    val email = event.email
    val cellPhone = event.cellPhone
    val subscriberDestination = event.subscriberDestination
    val cardNumber = event.cardNumber
    val detailWarranty = event.detailWarranty
    val endMostMessage = event.endMostMessage
    val recNewFrequency: com.experian.edf.oxygen.domain.internal.Frequency = if(event.nationalId == "00048187177") {
        Frequency.unique
      } else if(event.nationalId == "00075541227"){
      Frequency.onceEveryMonth
    }
    else null

    val name: SpanishName = curationService.curateName(fullName)

    new CreditCuratedEventJson(CreditCuratedEventStructured(
      idType,
      nationalId,
      accountNumber,
      name.firstName,
      name.lastName,
      name.secondFirstName,
      name.secondLastName,
      situationOfHolder,
      openningDate,
      dueDate,
      responsibleOrQualityOfDebtor,
      typeOfobligation,
      mortgageSubsidy,
      dateOfSubsidy,
      endOfThatContractCreatesTheObligation,
      wayToPay,
      frequencyOfPayment,
      novelty,
      stateOfOriginAccount,
      stateDateOrigin,
      stateOfMind,
      dateStatusAccount,
      stateOfPlastics,
      dateStatusPlastic,
      adjective,
      adjDate,
      classCard,
      franchise,
      brandNamePrivate,
      typeOfCurrency,
      typeOfWarranty,
      qualification,
      probabilityBreach,
      ageOfMora,
      creditLimit,
      balance,
      availableValue,
      valueMonthly,
      balanceValueInMora,
      totalFees,
      contributionsCancelled,
      arrears,
      clausePermanence,
      dateClausePermanence,
      paydayLimit,
      paymentDate,
      curationService.curateFilingOffice(officeOfFiling),
      cityOfFiling,
      daneCityCodeFiling,
      cityOfResidenceOrCommercialCity,
      daneCityCodeResidenceOrDaneCityCodeCommercial,
      departmentResidenceOrDepartmentCommercial,
      directionOfResidenceOrCommercialAddress,
      phoneResidenceOrBusinessPhone,
      cityLabor,
      daneCityCodeLabor,
      departmentLabor,
      workAddress,
      phoneWork,
      cityOfCorrespondence,
      daneCityCodeCorrespondence,
      departmentOfCorrespondence,
      directionOfCorrespondence,
      email,
      cellPhone,
      subscriberDestination,
      cardNumber,
      detailWarranty,
      isNewSubscriberRelation = if (nationalId == "00054591322") true else false,
      refersToRelation = if (nationalId == "00054591322") "garbageAcountNumber" else "",
      endMostMessage,
      recNewFrequency
    ))
  }

  override def subscriberRelation(taggedValidatedEvent: TaggedValidatedEvent): String = {
    val creditAcceptedEvent = new CreditAcceptedEventJson(taggedValidatedEvent.validatedRecordJson).creditEvent

    creditAcceptedEvent.accountNumber
  }

  /*
  override def createContextIndex(event: TaggedAcceptedEvent): ContextIndex = {
    val creditAcceptedEvent = new CreditAcceptedEventJson(event.acceptedRecord).creditEvent

    val lookups = mutable.Map[String, Array[String]]()
    lookups += ("AccountNumber" -> Array(event.lineage.dataSource,
      creditAcceptedEvent.accountNumber))
    new ContextIndex(lookups.toMap)
  } */

  override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = event.payload

  override val outputEventType: Int = 1
  /**
    * Curation Service
    */
  override val curationService: CurationRIService = new CurationRIService()
}
