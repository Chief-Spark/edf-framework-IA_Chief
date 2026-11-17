package com.experian.edf.oxygen.pinning.extension

class MatchRule() {

  /**
    * Holds the comparision results of all attributes
    * 64 Bits
    * 2 bits to hold the comparision results of each attribute
    * 11 : exact match
    * 10 : Partial Match
    * 01 : Do not match
    * 00 : null
    *
    */

  private var r: Long = 0

  private val exactMatch = 3
  private val partialMatch = 2
  private val doNotMatch = 1
  private val nullMatch = 0


  /**
    * Bit Positions of the attributes
    *
    * Phone: 0-1
    * Address: 2-3
    * Name: 4-5
    * IdNumber: 6-7
    * IdType: 8-9
    * TEC: 10-11
    */
  private val phonePosition = 0
  private val addressPosition = 2
  private val namePosition = 4
  private val idNumberPosition = 6
  private val idTypePosition = 8
  private val tecPosition = 10

  def idTypeIsNull(): Unit = {
    r = r | nullMatch << idTypePosition
  }
  def idTypeIsExactMatch(): Unit = {
    r = r | exactMatch << idTypePosition
  }
  def idTypeDoNotMatch(): Unit = {
    r = r | doNotMatch << idTypePosition
  }
  def idTypeIsPartialMatch(): Unit = {
    r = r
  }

}
