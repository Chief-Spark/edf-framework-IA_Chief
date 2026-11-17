package com.experian.edf.oxygen.consistencycheck.standalone.parser.base

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedCuratedEventWithMap}

/**
  * ConsistencyParserEvents trait is an umbrella/parent class inorder to have a type that represents
  * multiple types. We can define multiple classes that extend this parent trait (ConsistencyParserEvents)
  * and put ConsistencyParserEvents as the expected type in our functions.
  *
  * This is being used for all the consistency check parsers which represents all the events for the parser.
  */
sealed trait ConsistencyParserEvents extends Serializable
case class ParserTaggedCuratedEvent(value: TaggedCuratedEventWithMap) extends ConsistencyParserEvents
case class ParserSyntheticEvent(value: SyntheticEvent) extends ConsistencyParserEvents
case class ParserTaggedCuratedEventList(value: List[TaggedCuratedEventWithMap]) extends ConsistencyParserEvents
case class ParserSyntheticEventList(value: List[SyntheticEvent]) extends ConsistencyParserEvents

abstract class BaseParser[T1<:ConsistencyParserEvents, T2<:ConsistencyParserEvents] extends BaseConsistencyParser {
}

