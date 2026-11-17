package com.experian.edf.oxygen.acceptance.engines.dsl

import scala.util.parsing.input.Positional

/**
 * List of tokens supported in Acceptance statements.
 *
 */
sealed trait AcceptanceToken extends Positional

case class ANYOF(conds: List[String]) extends AcceptanceToken

case class ALLOF(conds: List[String]) extends AcceptanceToken

case class HASERRORLEVELIN(vals: List[Int]) extends AcceptanceToken

case class HASERRORLEVELNOTIN(vals: List[Int]) extends AcceptanceToken
