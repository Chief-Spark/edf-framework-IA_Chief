package com.experian.edf.oxygen.validation.extension.metadata


import com.experian.edf.oxygen.domain.internal.FileMetadata

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
  * Created by c15661a on 8/7/17.
  */
class CreditFileMetadata(fileName: String, headers: Array[String]) extends FileMetadata(fileName, headers) {

  override def headerRowCount: Int = 1

  val asOfDate = if (headers.length > 0 && headers.length ==34) {
      ZonedDateTime
        .parse(headers.last.substring(26, 34).trim + " 00:00:00 Etc/UTC",
          DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss VV"))
        .toInstant.toEpochMilli
    } else
    ZonedDateTime
      .parse("20170202 00:00:00 Etc/UTC", DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss VV"))
      .toInstant.toEpochMilli

  override val dataSource: String = extractFields(fileName)._1


  override val receivedDate = asOfDate

  def extractFields(fileName: String): (String, String) = {
    // val filePattern = "(.*checkingSavingsMaster/)(\\d{6}).(\\d{8}).(F)".r
    //    val filePattern = if(fileName.endsWith("xml")) {".*.xml".r} else {"(.*[E|X]-)(\\d{6}).(\\d{8}).T".r}
    val filePattern = if (fileName.endsWith("xml")) {
      "(.*[E|X]-)(\\d{6}).(\\d{8}).xml".r
    } else {
      "(.*[E|X]-)(\\d{6}).(\\d{8}).T".r
    }
    //    val filePattern = "(.*[E|X]-)(\\d{6}).(\\d{8}).T".r
    val filePattern(prefix, subscriber, cutOffDate) = fileName
    (subscriber.toString, cutOffDate)
  }

  override val subscriber: String = extractFields(fileName)._1

  /**
    * Must be in epoch time
    */
  override val fileAsOfDate: Long = asOfDate

  override val fileTag: String = if (extractFields(fileName)._2.contains("201905")) "sisben"
  else if(extractFields(fileName)._2.contains("201906")) "jurad"
  else "judicial"

  override val fileType: String = if (extractFields(fileName)._2.equals("20171130") || extractFields(fileName)._2.equals("20181031")) "Checking" else "Credit"

  override val subSubscriber: String = extractFields(fileName)._2

  override val additionalInformation: Map[String, String] = Map("fileType" -> fileType, "nit" -> subscriber, "subscriberCode" -> subSubscriber, "datasource" -> dataSource)
}
