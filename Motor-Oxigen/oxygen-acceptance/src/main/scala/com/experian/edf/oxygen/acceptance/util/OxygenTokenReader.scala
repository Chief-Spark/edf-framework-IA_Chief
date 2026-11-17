package com.experian.edf.oxygen.acceptance.util

import scala.util.parsing.input.{NoPosition, Position, Positional, Reader}

/**
 * A token reader to handle a Sequence of Positional tokens.
 *
 * @param tokens Sequence of Tokens
 * @tparam T Positional or derived type.
 */
class OxygenTokenReader[T <: Positional](tokens: Seq[T]) extends Reader[T] {
  override def first: T = tokens.head

  override def rest: Reader[T] = new OxygenTokenReader(tokens.tail)

  override def pos: Position = tokens.headOption.map(_.pos).getOrElse(NoPosition)

  override def atEnd: Boolean = tokens.isEmpty
}