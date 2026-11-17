package com.experian.edf.oxygen.consistencycheck.standalone.parser.ast

import scala.util.parsing.input.Positional

/**
 * Lists of tokens
 * @author c22603a - Annadatha, Annapurna
 *
 **/
/**
 * List of all possible tokens that can be extracted for Consistency Check expressions
 */
sealed abstract class ConsistencyCheckToken extends Positional

case object THISEVENT extends ConsistencyCheckToken
case object THATEVENT extends ConsistencyCheckToken
case object EXISTS extends ConsistencyCheckToken
case object GLOBAL extends ConsistencyCheckToken
case object DOT extends ConsistencyCheckToken
case object EQUALS extends ConsistencyCheckToken
case object ASSIGN extends ConsistencyCheckToken
case object NOTEQUAL extends ConsistencyCheckToken
case object GREATERTHAN extends ConsistencyCheckToken
case object GREATEROREQUAL extends ConsistencyCheckToken
case object LESSTHAN extends ConsistencyCheckToken
case object LESSOREQUAL extends ConsistencyCheckToken
case object AND extends ConsistencyCheckToken
case object OR extends ConsistencyCheckToken
case class IDENTIFIER(str: String) extends ConsistencyCheckToken
case class THISIDENTIFIER(str: String) extends ConsistencyCheckToken
case class THATIDENTIFIER(str: String) extends ConsistencyCheckToken
case class THISGLOBALIDENTIFIER(str: String) extends ConsistencyCheckToken
case class THATGLOBALIDENTIFIER(str: String) extends ConsistencyCheckToken
case object ANYINDENTIFIER extends ConsistencyCheckToken
case class NUMBER(n: Number) extends ConsistencyCheckToken
case class LITERAL(str: String) extends ConsistencyCheckToken
case object LEFTPARENTHESIS extends ConsistencyCheckToken
case object RIGHTPARENTHESIS extends ConsistencyCheckToken
case object TRUE extends ConsistencyCheckToken
case object FALSE extends ConsistencyCheckToken
case object COMMASEPERATOR extends ConsistencyCheckToken
case object FIRST extends ConsistencyCheckToken
case object LAST extends ConsistencyCheckToken
case object MOSTRECENT extends ConsistencyCheckToken
case object CHECKSTATE extends ConsistencyCheckToken
case object PREV extends ConsistencyCheckToken
case object ANYONE extends ConsistencyCheckToken
case object REFERENCEEVENTCOUNT extends ConsistencyCheckToken
case object IN extends ConsistencyCheckToken
case object NOTIN extends ConsistencyCheckToken
case class  SEQUENCE(list: List[Any]) extends ConsistencyCheckToken
case class INLIST(list: List[Any]) extends ConsistencyCheckToken
case class NOTINLIST(list: List[Any]) extends ConsistencyCheckToken
case class EXTERNALTABLE(str: String) extends ConsistencyCheckToken
case class SELECT(str: String) extends ConsistencyCheckToken
case object FILTER extends ConsistencyCheckToken
case object WILLBEMIGRATED extends ConsistencyCheckToken


sealed abstract class TriggerCompilationError

case class TriggerLexerError(location: Location, msg: String) extends TriggerCompilationError
case class TriggerParserError(location: Location, msg: String) extends TriggerCompilationError

case class Location(line: Int, column: Int) {
  override def toString = s"$line:$column"
}
