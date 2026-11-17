package com.experian.edf.oxygen.validation.validator

import com.experian.edf.oxygen.validator.internal.Errors

/**
  * Created by c20286a on 7/20/17.
  * This is the validation of the framework classes, not to be
  * confused with the "validation" phase of the oxygen framework.
  * The validation phase is the first phase of the pipeline, followed by acceptance
  * followed by curation and then pinning. This class HAS nothing to do with
  * these phases, but is an interface for the error handling of the framework.
  */
abstract class OxyValidator(val objectName: String) extends Serializable {

  def supports(clazz: Class[_]): Boolean
  def validate(target: Any, errors: Errors) : Errors
}
