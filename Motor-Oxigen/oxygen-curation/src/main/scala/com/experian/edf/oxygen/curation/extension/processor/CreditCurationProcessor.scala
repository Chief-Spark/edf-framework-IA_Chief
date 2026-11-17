package com.experian.edf.oxygen.curation.extension.processor

import com.experian.edf.oxygen.curation.extension.service.CurationRIService
import com.experian.edf.oxygen.curation.extension.util.Util
import com.experian.edf.oxygen.curation.extension.event.CreditCuratedEvent
import com.experian.edf.oxygen.curation.extension.validation.CreditAcceptedEvent
import com.experian.edf.oxygen.curation.domain.ValidatedEvent
import com.experian.edf.oxygen.curation.engines.CurationProcessor
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.domain.internal.{CuratedEvent, Payload}
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import org.slf4j.LoggerFactory._


class CreditCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context: CurationProcessorContext) {

  @transient lazy val logger = getLogger(this.getClass)

  /**
    * Implementation specific method
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
    * Method returns a curatedEvent after processing an acceptedEvent.
    * In this implementation, we expect the AcceptedEvent class to provide the methods
    * to return all the appropriate fields.
    *
    * As a part of the curation, here we only curate fullname using a simple nameParser and
    * we pass through all the other fields as in to create a CuratedEvent.
    *
    * @param taggedAcceptedEvent AcceptedEvent : The accepted event which needs to be curated
    * @return CuratedEvent
    */
  override def curateEvent(taggedAcceptedEvent: TaggedValidatedEvent): CuratedEvent = {

    val creditAcceptedEvent = new CreditAcceptedEvent(taggedAcceptedEvent.validatedRecordJson)
    val oxygenLookupServ = context.lookup
    val lookupRetValue = Util.getLookup("consistencyfiltertable1", Map("errorcode" -> "errorCode1"),"errorvalue", oxygenLookupServ )
    logger.error(s"*****************" + lookupRetValue.head)


    // val lookupVals = lookup.get("states", Map("State"->"Alabama", "Abbreviation"->"AL1"))
    // val lookupVals = lookup.get("BDM_DATOS.ESTADO_RECLAMO",Map("COD_DW_EST_RECLAMO"->"1","NOMBRE_EST_RECLAMO"->"APLICADO","DESC_EST_RECLAMO"->"APLICADO"))
    // val lookupVals = lookup.get("account_event_qa",Map("account_type"->"1"))
    // lookupVals.get.foreach(elem => logger.info("account_type is " + elem("account_type")))

    val idType = creditAcceptedEvent.idType
    val nationalId = creditAcceptedEvent.nationalId
    val fullName = creditAcceptedEvent.fullName
    val accountNumber = creditAcceptedEvent.accountNumber
    val situationOfHolder = creditAcceptedEvent.situationOfHolder
    val openningDate = creditAcceptedEvent.openningDate
    val dueDate = creditAcceptedEvent.dueDate
    val responsibleOrQualityOfDebtor = creditAcceptedEvent.responsibleOrQualityOfDebtor
    val typeOfobligation = creditAcceptedEvent.typeOfobligation
    val mortgageSubsidy = creditAcceptedEvent.mortgageSubsidy
    val dateOfSubsidy = creditAcceptedEvent.dateOfSubsidy
    val endOfThatContractCreatesTheObligation = creditAcceptedEvent.endOfThatContractCreatesTheObligation
    val wayToPay = creditAcceptedEvent.wayToPay
    val frequencyOfPayment = creditAcceptedEvent.frequencyOfPayment
    val novelty = creditAcceptedEvent.novelty
    val stateOfOriginAccount = creditAcceptedEvent.stateOfOriginAccount
    val stateDateOrigin = creditAcceptedEvent.stateDateOrigin
    val stateOfMind = creditAcceptedEvent.stateOfMind
    val dateStatusAccount = creditAcceptedEvent.dateStatusAccount
    val stateOfPlastics = creditAcceptedEvent.stateOfPlastics
    val dateStatusPlastic = creditAcceptedEvent.dateStatusPlastic
    val adjective = creditAcceptedEvent.adjective
    val adjDate = creditAcceptedEvent.adjDate
    val classCard = creditAcceptedEvent.classCard
    val franchise = creditAcceptedEvent.franchise
    val brandNamePrivate = creditAcceptedEvent.brandNamePrivate
    val typeOfCurrency = creditAcceptedEvent.typeOfCurrency
    val typeOfWarranty = creditAcceptedEvent.typeOfWarranty
    val qualification = creditAcceptedEvent.qualification
    val probabilityBreach = creditAcceptedEvent.probabilityBreach
    val ageOfMora = creditAcceptedEvent.ageOfMora
    val initialValue = creditAcceptedEvent.initialValue
    val debtBalance = creditAcceptedEvent.debtBalance
    val availableValue = creditAcceptedEvent.availableValue
    val valueMonthly = creditAcceptedEvent.valueMonthly
    val balanceValueInMora = creditAcceptedEvent.balanceValueInMora
    val totalFees = creditAcceptedEvent.totalFees
    val contributionsCancelled = creditAcceptedEvent.contributionsCancelled
    val arrears = creditAcceptedEvent.arrears
    val clausePermanence = creditAcceptedEvent.clausePermanence
    val dateClausePermanence = creditAcceptedEvent.dateClausePermanence
    val paydayLimit = creditAcceptedEvent.paydayLimit
    val paymentDate = creditAcceptedEvent.paymentDate
    val officeOfFiling = creditAcceptedEvent.officeOfFiling
    val cityOfFiling = creditAcceptedEvent.cityOfFiling
    val daneCityCodeFiling = creditAcceptedEvent.daneCityCodeFiling
    val cityOfResidenceOrCommercialCity = creditAcceptedEvent.cityOfResidenceOrCommercialCity
    val daneCityCodeResidenceOrDaneCityCodeCommercial = creditAcceptedEvent.daneCityCodeResidenceOrDaneCityCodeCommercial
    val departmentResidenceOrDepartmentCommercial = creditAcceptedEvent.departmentResidenceOrDepartmentCommercial
    val directionOfResidenceOrCommercialAddress = creditAcceptedEvent.directionOfResidenceOrCommercialAddress
    val phoneResidenceOrBusinessPhone = creditAcceptedEvent.phoneResidenceOrBusinessPhone
    val cityLabor = creditAcceptedEvent.cityLabor
    val daneCityCodeLabor = creditAcceptedEvent.daneCityCodeLabor
    val departmentLabor = creditAcceptedEvent.departmentLabor
    val workAddress = creditAcceptedEvent.workAddress
    val phoneWork = creditAcceptedEvent.phoneWork
    val cityOfCorrespondence = creditAcceptedEvent.cityOfCorrespondence
    val daneCityCodeCorrespondence = creditAcceptedEvent.daneCityCodeCorrespondence
    val departmentOfCorrespondence = creditAcceptedEvent.departmentOfCorrespondence
    val directionOfCorrespondence = creditAcceptedEvent.directionOfCorrespondence
    val email = creditAcceptedEvent.email
    val cellPhone = creditAcceptedEvent.cellPhone
    val subscriberDestination = creditAcceptedEvent.subscriberDestination
    val cardNumber = creditAcceptedEvent.cardNumber
    val detailWarranty = creditAcceptedEvent.detailWarranty

    val name = curationService.curateName(fullName)

    new CreditCuratedEvent(
      idType,
      nationalId,
      accountNumber,
      name.firstName,
      name.lastName,
      name.secondFirstName,
      name.secondLastName,
      situationOfHolder,
      openningDate,
      Some(dueDate),
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
      initialValue,
      debtBalance,
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
      officeOfFiling,
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
      detailWarranty
    )
  }
  /*

  override def createContextIndex(event: TaggedAcceptedEvent): ContextIndex = {
    val creditAcceptedEvent = new CreditAcceptedEvent(event.acceptedRecord)

    val lookups = mutable.Map[String, Array[String]]()
    lookups += ("AccountNumber" -> Array(event.lineage.dataSource,
      creditAcceptedEvent.accountNumber))
    new ContextIndex(lookups.toMap)
  } */

  override def enrichEventWithPayload(event: TaggedValidatedEvent): Payload = {
    event.payload
  }

  override def subscriberRelation(taggedValidatedEvent: TaggedValidatedEvent): String = {
    val creditAcceptedEvent = new CreditAcceptedEvent(taggedValidatedEvent.validatedRecordJson)

    creditAcceptedEvent.accountNumber
  }

  override val outputEventType: Int = 1
  /**
    * Curation Service
    */
  override val curationService: CurationRIService = new CurationRIService()
}
