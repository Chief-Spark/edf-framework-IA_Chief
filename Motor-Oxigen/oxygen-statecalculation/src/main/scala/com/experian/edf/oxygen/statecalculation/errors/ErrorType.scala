package com.experian.edf.oxygen.statecalculation.errors

/**
  *
  * @author Abraham, Thomas - C15627A
  *         Created on 2019-05-01 at 08:27
  */
trait ErrorType extends Serializable {
  val message: String
  val code: String
  val additionalInformation: String
  val severity: ErrorSeverity

  override def toString: String = s"$code | $severity | $message"
}

case class StateCalculationConfigurationError(override val code: String, override val message: String, override val severity: ErrorSeverity = ErrorSeverity.INFO, override val additionalInformation: String = "") extends ErrorType
