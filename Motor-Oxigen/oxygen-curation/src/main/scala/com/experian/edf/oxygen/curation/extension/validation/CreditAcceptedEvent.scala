package com.experian.edf.oxygen.curation.extension.validation

import com.experian.edf.oxygen.curation.domain.ValidatedEvent
import com.experian.edf.oxygen.curation.extension.util.Util
import com.google.gson.Gson
import org.slf4j.LoggerFactory.getLogger

/**
  * Created by c15661a on 8/7/17.
  */
class CreditAcceptedEvent(rec: String) extends ValidatedEvent(rec) {

  @transient lazy val logger = getLogger(this.getClass)
  @transient lazy val gson = new Gson()
  override def read(rawRecord: String): CreditAcceptedEvent = new CreditAcceptedEvent(rawRecord)

  override def write: String = gson.toJson(this)


  val idType: Int = {
    Util.toInt(rec.substring(0, 1)).getOrElse(-1)
  }

  val nationalId: String = {
    rec.substring(1, 12).trim
  }

  val accountNumber: String = {
    rec.substring(12, 30)
  }

  val fullName: String = {
    if (rec.length < 75)
      rec.substring(30, rec.length).trim
    else
      rec.substring(30, 75).trim
  }

  val situationOfHolder: Int = {
    if (rec.length > 75) {
      if (rec.length < 76)
        Util.toInt(rec.substring(75, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(75, 76)).getOrElse(-1)
    } else
      -1
  }

  val openningDate: Int = {
    if (rec.length > 76) {
      if (rec.length < 84)
        Util.toInt(rec.substring(76, rec.length)).getOrElse(-1)
      else
        Util.toInt(rec.substring(76, 84)).getOrElse(-1)
    } else
      -1
  }

  val dueDate: Int = {
    if (rec.length > 84) {
      if (rec.length < 92)
        Util.toInt(rec.substring(84, rec.length)).getOrElse(-1)
      else
        Util.toInt(rec.substring(84, 92)).getOrElse(-1)
    } else
      -1
  }

  val responsibleOrQualityOfDebtor: Int = {
    if (rec.length > 92) {
      if (rec.length < 94)
        Util.toInt(rec.substring(92, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(92, 94)).getOrElse(-1)
    } else -1
  }

  val typeOfobligation: Int = {
    if (rec.length > 94)
      Util.toInt(rec.substring(94, 95)).getOrElse(-1)
    else -1
  }

  val mortgageSubsidy: Int = {
    if (rec.length > 95)
      Util.toInt(rec.substring(95, 96).trim).getOrElse(-1)
    else -1
  }

  val dateOfSubsidy: Int = {
    if (rec.length > 96) {
      if (rec.length < 104)
        Util.toInt(rec.substring(96, rec.length)).getOrElse(-1)
      else
        Util.toInt(rec.substring(96, 104)).getOrElse(-1)
    } else -1

  }

  val endOfThatContractCreatesTheObligation: Int = {
    if (rec.length > 104)
      Util.toInt(rec.substring(104, 105)).getOrElse(-1)
    else -1
  }

  val wayToPay: Int = {
    if (rec.length > 105)
      Util.toInt(rec.substring(105, 106)).getOrElse(-1)
    else -1
  }

  val frequencyOfPayment: Int = {
    if (rec.length > 106)
      Util.toInt(rec.substring(106, 107)).getOrElse(-1)
    else -1
  }

  val novelty: Int = {
    if (rec.length > 107) {
      if (rec.length < 109)
        Util.toInt(rec.substring(107, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(107, 109).trim).getOrElse(-1)
    } else -1
  }

  val stateOfOriginAccount: Int = {
    if (rec.length > 109)
      Util.toInt(rec.substring(109, 110)).getOrElse(-1)
    else -1
  }

  val stateDateOrigin: Int = {
    if (rec.length > 110) {
      if (rec.length < 118)
        Util.toInt(rec.substring(110, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(110, 118).trim).getOrElse(-1)
    } else -1
  }

  // state of account: annex 2.7
  val stateOfMind: Int = {
    if (rec.length > 118) {
      if (rec.length < 120)
        Util.toInt(rec.substring(118, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(118, 120).trim).getOrElse(-1)
    } else -1
  }

  val dateStatusAccount: Int = {
    if (rec.length > 120) {
      if (rec.length < 128)
        Util.toInt(rec.substring(120, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(120, 128).trim).getOrElse(-1)
    } else -1
  }

  val stateOfPlastics: Int = {
    if (rec.length > 128)
      Util.toInt(rec.substring(128, 129)).getOrElse(-1)
    else -1
  }

  val dateStatusPlastic: Int = {
    if (rec.length > 129) {
      if (rec.length < 137)
        Util.toInt(rec.substring(129, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(129, 137).trim).getOrElse(-1)
    } else -1
  }

  val adjective: Int = {
    if (rec.length > 137)
      Util.toInt(rec.substring(137, 138)).getOrElse(-1)
    else -1
  }

  val adjDate: Int = {
    if (rec.length > 138) {
      if (rec.length < 146)
        Util.toInt(rec.substring(138, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(138, 146).trim).getOrElse(-1)
    } else -1
  }

  val classCard: Int = {
    if (rec.length > 146)
      Util.toInt(rec.substring(146, 147)).getOrElse(-1)
    else -1

  }

  val franchise: Int = {
    if (rec.length > 147)
      Util.toInt(rec.substring(147, 148)).getOrElse(-1)
    else -1
  }

  val brandNamePrivate: String = {
    if (rec.length > 148) {
      if (rec.length < 178)
        rec.substring(148, rec.length).trim
      else
        rec.substring(148, 178).trim
    } else ""
  }

  val typeOfCurrency: Int = {
    if (rec.length > 178)
      Util.toInt(rec.substring(178, 179)).getOrElse(-1)
    else
      -1
  }

  val typeOfWarranty: Int = {
    if (rec.length > 179)
      Util.toInt(rec.substring(179, 180)).getOrElse(-1)
    else -1
  }

  val qualification: String = {
    if (rec.length > 180) {
      if (rec.length < 182)
        rec.substring(180, rec.length).trim
      else
        rec.substring(180, 182).trim
    } else ""
  }

  val probabilityBreach: Int = {
    if (rec.length > 182) {
      if (rec.length < 185)
        Util.toInt(rec.substring(182, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(182, 185).trim).getOrElse(-1)
    } else -1
  }

  val ageOfMora: Int = {
    if (rec.length > 185) {
      if (rec.length < 188)
        Util.toInt(rec.substring(185, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(185, 188).trim).getOrElse(-1)
    } else -1
  }

  val initialValue: Long = {
    if (rec.length > 188) {
      if (rec.length < 199)
        Util.toLong(rec.substring(188, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(188, 199).trim).getOrElse(-1)
    } else -1L
  }

  val debtBalance: Long = {
    if (rec.length > 199) {
      if (rec.length < 210)
        Util.toLong(rec.substring(199, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(199, 210).trim).getOrElse(-1)
    } else -1L
  }

  // available balance / revolving credit
  val availableValue: Long = {
    if (rec.length > 210) {
      if (rec.length < 221)
        Util.toLong(rec.substring(210, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(210, 221).trim).getOrElse(-1)
    } else -1L
  }

  // value of monthly payment / installment / payment amount
  val valueMonthly: Long = {
    if (rec.length > 221) {
      if (rec.length < 232)
        Util.toLong(rec.substring(221, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(221, 232).trim).getOrElse(-1)
    } else -1L
  }

  // value of balance overdue
  val balanceValueInMora: Long = {
    if (rec.length > 232) {
      if (rec.length < 243)
        Util.toLong(rec.substring(232, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(232, 243).trim).getOrElse(-1)
    } else -1L
  }

  val totalFees: Int = {
    if (rec.length > 243) {
      if (rec.length < 246)
        Util.toInt(rec.substring(243, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(243, 246).trim).getOrElse(-1)
    } else -1
  }

  val contributionsCancelled: Int = {
    if (rec.length > 246) {
      if (rec.length < 249)
        Util.toInt(rec.substring(246, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(246, 249)).getOrElse(-1)
    } else -1
  }

  val arrears: Int = {
    if (rec.length > 249) {
      if (rec.length < 252)
        Util.toInt(rec.substring(249, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(249, 252).trim).getOrElse(-1)
    } else -1
  }

  val clausePermanence: Int = {
    if (rec.length > 252) {
      if (rec.length < 255)
        Util.toInt(rec.substring(252, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(252, 255)).getOrElse(-1)
    } else -1
  }

  val dateClausePermanence: Int = {
    if (rec.length > 255) {
      if (rec.length < 263)
        Util.toInt(rec.substring(255, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(255, 263).trim).getOrElse(-1)
    } else -1

  }

  // deadline for payment
  val paydayLimit: Int = {
    if (rec.length > 263) {
      if (rec.length < 271)
        Util.toInt(rec.substring(263, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(263, 271).trim).getOrElse(-1)
    } else -1

  }

  val paymentDate: Int = {
    if (rec.length > 271) {
      if (rec.length < 279)
        Util.toInt(rec.substring(271, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(271, 279).trim).getOrElse(-1)
    } else -1
  }

  val officeOfFiling: String = {
    if (rec.length > 279) {
      if (rec.length < 309)
        rec.substring(279, rec.length).trim
      else
        rec.substring(279, 309).trim
    } else ""
  }

  val cityOfFiling: String = {
    if (rec.length > 309) {
      if (rec.length < 329)
        rec.substring(309, rec.length).trim
      else
        rec.substring(309, 329).trim
    } else ""
  }

  val daneCityCodeFiling: Int = {
    if (rec.length > 329) {
      if (rec.length < 337)
        Util.toInt(rec.substring(329, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(329, 337)).getOrElse(-1)
    } else -1
  }

  val cityOfResidenceOrCommercialCity: String = {
    if (rec.length > 337) {
      if (rec.length < 357)
        rec.substring(337, rec.length).trim()
      else
        rec.substring(337, 357).trim()
    } else ""
  }

  val daneCityCodeResidenceOrDaneCityCodeCommercial: Int = {
    if (rec.length > 357) {
      if (rec.length < 365)
        Util.toInt(rec.substring(357, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(357, 365).trim).getOrElse(-1)
    } else -1
  }

  val departmentResidenceOrDepartmentCommercial: String = {
    if (rec.length > 365) {
      if (rec.length < 385)
        rec.substring(365, rec.length).trim
      else
        rec.substring(365, 385).trim
    } else ""
  }

  val directionOfResidenceOrCommercialAddress: String = {
    if (rec.length > 385) {
      if (rec.length < 445)
        rec.substring(385, rec.length).trim
      else
        rec.substring(385, 445).trim
    } else ""
  }

  val phoneResidenceOrBusinessPhone: Long = {
    if (rec.length > 445) {
      if (rec.length < 457)
        Util.toLong(rec.substring(445, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(445, 457).trim).getOrElse(-1)
    } else -1L
  }

  val cityLabor: String = {
    if (rec.length > 457) {
      if (rec.length < 477)
        rec.substring(457, rec.length).trim
      else
        rec.substring(457, 477).trim
    } else ""
  }

  val daneCityCodeLabor: Int = {
    if (rec.length > 477) {
      if (rec.length < 485)
        Util.toInt(rec.substring(477, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(477, 485).trim).getOrElse(-1)
    } else -1
  }

  val departmentLabor: String = {
    if (rec.length > 485) {
      if (rec.length < 505)
        rec.substring(485, rec.length).trim
      else
        rec.substring(485, 505).trim
    } else ""
  }

  val workAddress: String = {
    if (rec.length > 505) {
      if (rec.length < 565)
        rec.substring(505, rec.length).trim
      else
        rec.substring(505, 565).trim
    } else ""
  }

  val phoneWork: Int = {
    if (rec.length > 565) {
      if (rec.length < 577)
        Util.toInt(rec.substring(565, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(565, 577).trim).getOrElse(-1)
    } else -1
  }

  val cityOfCorrespondence: String = {
    if (rec.length > 577) {
      if (rec.length < 597)
        rec.substring(577, rec.length).trim
      else
        rec.substring(577, 597).trim
    } else ""
  }

  val daneCityCodeCorrespondence: Int = {
    if (rec.length > 597) {
      if (rec.length < 605)
        Util.toInt(rec.substring(597, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(597, 605).trim).getOrElse(-1)
    } else -1
  }

  val departmentOfCorrespondence: String = {
    if (rec.length > 605) {
      if (rec.length < 625)
        rec.substring(605, rec.length).trim
      else
        rec.substring(605, 625).trim
    } else ""
  }

  val directionOfCorrespondence: String = {
    if (rec.length > 625) {
      if (rec.length < 685)
        rec.substring(625, rec.length).trim
      else
        rec.substring(625, 685).trim
    } else ""
  }

  val email: String = {
    if (rec.length > 685) {
      if (rec.length < 745)
        rec.substring(685, rec.length).trim
      else
        rec.substring(685, 745).trim
    } else ""
  }

  val cellPhone: Long = {
    if (rec.length > 745) {
      if (rec.length < 757)
        Util.toLong(rec.substring(745, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(745, 757).trim).getOrElse(-1)
    } else -1L
  }

  val subscriberDestination: Int = {
    if (rec.length > 757) {
      if (rec.length < 763)
        Util.toInt(rec.substring(757, rec.length).trim).getOrElse(-1)
      else
        Util.toInt(rec.substring(757, 763).trim).getOrElse(-1)
    } else -1
  }

  val cardNumber: Long = {
    if (rec.length > 763) {
      if (rec.length < 781)
        Util.toLong(rec.substring(763, rec.length).trim).getOrElse(-1)
      else
        Util.toLong(rec.substring(763, 781).trim).getOrElse(-1)
    } else -1L
  }

  val detailWarranty: String = {
    if (rec.length > 781)
      rec.substring(781, 782).trim
    else ""
  }

}
