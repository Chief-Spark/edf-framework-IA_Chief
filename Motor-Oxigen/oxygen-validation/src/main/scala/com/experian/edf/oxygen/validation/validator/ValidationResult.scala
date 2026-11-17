package com.experian.edf.oxygen.validation.validator

import com.experian.edf.oxygen.validator.internal.ObjectError

import scala.collection.mutable.ListBuffer

object ValidationType extends Enumeration {
  type Level = Value
  val Success, Failure, Error, Warn, Info = Value
}

trait OxyLoggable extends Serializable {
  def data: String

}

class ValidationData extends OxyLoggable {
  override def data: String = ""
}

/**
  * This is a base class for validation results. A result can either be a success of not.
  * Eg. A failure may be a fatal error or just a failure (not fatal). If the validation is successful,
  * we provide a result back to the invoker. The result will be of type 'T'. We also have a provision
  * of pushing in a list of statuses, error codes, etc. This can be returned to the framework and used
  * for logging or taking other actions like acceptance, rejection, etc.
  * @param result
  * @param errors
  *
  */
abstract class ValidationResult(val result: ValidationData, val errors: Array[ObjectError], val successful : Boolean) extends Serializable {

  val successLevel : ValidationType.Value

  // Get the result
  def get: ValidationData

  // Get the embedded result or else
  def getOrElse[B >: Any] (default: => B) : B =
    if (isEmpty) default else this.get

  // Is there an error. successful = false is any nosuccess (error, failure, fatal, etc.)
  val isEmpty = successful

  def log : List[String] = {

    val buffer = new ListBuffer[String]

    // Log the Status of the result
    buffer.append("Status: " + successLevel)
    // Invoke the log method of the result object
    buffer.append(result.data)

    // Run through each of the error objects and print them line by line
    errors.foreach(error => {
      buffer.append("Error Code: " + error.errorCodeName)
      buffer.append("Error Message: " + error.errorCodeValue)
      buffer.append("Error Level: " + error.errorLevel)
      buffer.append("Error Type: " + error.errorType)
    })
    buffer.toList
  }
}

/**
  * The success case of ValidationResult. Contains a result and errors.
  * @param result
  * @param errors
  * @tparam T
  */
class ValidationSuccess (override val result: ValidationData, override val errors: Array[ObjectError]) extends ValidationResult(result, errors, true) {
  override def get = result
  override val successLevel: ValidationType.Value = ValidationType.Success
}

/**
  * A common parent for unsuccessful validation results. The result type is Nothing here.
  * @param errors
  */
class ValidationFailure(override val result: ValidationData, override val errors: Array[ObjectError]) extends ValidationResult(result, errors, false) {
  override val successLevel: ValidationType.Value = ValidationType.Failure
  def get: Nothing = scala.sys.error("No validation result on failure")
}

class ValidationError(override val result: ValidationData, override val errors: Array[ObjectError]) extends ValidationResult(result, errors, false) {
  override val successLevel: ValidationType.Value = ValidationType.Error
  def get: Nothing = scala.sys.error("No validation result on failure")
}

class ValidationInfo(override val result: ValidationData, override val errors: Array[ObjectError]) extends ValidationSuccess(result, errors) {
  override val successLevel: ValidationType.Value = ValidationType.Info
}

class ValidationWarn(override val result: ValidationData, override val errors: Array[ObjectError]) extends ValidationSuccess(result, errors) {
  override val successLevel: ValidationType.Value = ValidationType.Warn
}
