package com.experian.edf.oxygen.curation.domain

import scala.collection.mutable.ArrayBuffer

case class QualityScore(name: String, score: Double) extends Serializable {
}

object QualityScore extends Serializable {
  def read(qualityScoreStr: String): Array[QualityScore] = {
    val qs = new ArrayBuffer[QualityScore]
    if (qualityScoreStr != null && qualityScoreStr != "") {
      qualityScoreStr.split(",").foreach(x => {
        val kv = x.split(":")
        qs += new QualityScore(kv(0), kv(1).toDouble)
      })
    }
    qs.toArray
  }

  def toString(qualityScores: Array[QualityScore]): String = {
    if(qualityScores == null || qualityScores.isEmpty)
      ""
    else
      qualityScores.map(qs => qs.name + ":" + qs.score.toString).mkString(",")
  }
}