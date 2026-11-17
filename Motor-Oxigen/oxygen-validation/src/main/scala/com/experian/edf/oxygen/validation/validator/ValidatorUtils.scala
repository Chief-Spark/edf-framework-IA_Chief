package com.experian.edf.oxygen.validation.validator

import com.experian.edf.oxygen.validator.internal.Errors

import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory._
/**
  * Created by c20286a on 7/18/17.
  * This is a utility class to provide basic validation
  * functionality.
  */
object ValidatorUtils extends Serializable {

  @transient lazy val logger = getLogger(ValidatorUtils.getClass)

  def invokeValidator(validator: OxyValidator, objToValidate : Any, errors : Errors) : Errors = {

    assert(true == validator.supports(objToValidate.getClass))
    validator.validate(objToValidate, errors)
  }

  // Nested Method to check for errors in settings for certain keys
  def rejectIfKeyNotFound(key: String, map: ConcurrentHashMap[String,Any], errors: Errors) : Errors = {
    logger.info("I am trying to look for a property called '" + key + "' in the configuration")
    if (!map.containsKey(key)) {
      val msg = "I need a configuration property called '" + key + "' but I could not find it..."
      errors.reject(key + ".not.set", msg)
      logger.error(msg)
    }
    else
      logger.info("Looks good so far! I found the configuration property called '" + key + "' with a value of " + map.get(key))

    errors
  }

}
