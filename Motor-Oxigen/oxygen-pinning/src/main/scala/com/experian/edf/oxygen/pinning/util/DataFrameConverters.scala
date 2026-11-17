package com.experian.edf.oxygen.pinning.util

import com.experian.edf.oxygen.domain.{SyntheticEvent, TaggedIdentity}
import com.experian.edf.oxygen.domain.internal.StampedIdentity
import com.experian.edf.oxygen.hydrator.core.dehydrator.{StampedIdentityPersistable, SynthesizedEventsPersistable}
import com.experian.edf.oxygen.stage.statecalculation.XpmModel
import com.experian.edf.oxygen.utils.RDDUtils
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}

//TODO: Thomas - Creationg this as a stop gap while refactoring. Need to think through on where to put this and how to have it.
object DataFrameConverters extends Serializable {

  /**
   * Creates a DataFrame from an RDD of StampedIdentity objects.
   *
   * The returned DataFrame is the exact format required for use with the
   * NearNeighbors Hydrator.
   *
   * Its structure should not be modified before being written out by the
   * Hydrator.
   *
   * @param spark A SparkSession
   * @param stampedIdentities An RDD of StampedIdentity objects to prepare for hydration.
   * @return A DataFrame for use with the StampedIdentityHydrator hydrate method.
   */
  def forStampedIdentity(spark: SparkSession, stampedIdentities: RDD[StampedIdentity]): DataFrame = {
    val stampedIdentitiesPersistable = stampedIdentities.map(x => StampedIdentityPersistable(String.valueOf(x.identityId), x))
    RDDUtils.toDataFrame(spark, stampedIdentitiesPersistable)
  }

  /**
   * Creates a DataFrame from an RDD of (Key, Array[TaggedIdentity])
   *
   * The returned DataFrame is in the exact format required for use with the
   * NearNeighbors Hydrator.
   *
   * Its structure should not be modified before being written out by the
   * Hydrator.
   *
   * @param spark A SparkSession
   * @param nearNeighbors A Pair RDD of type (String, Array[TaggedIdentity) where
   *                      the String is the nearNeighbor id.
   * @return A DataFrame for use with the NearNeighborsHydrator hydrate method.
   */
  def forTaggedIdentity(spark: SparkSession, nearNeighbors: RDD[(String, Array[TaggedIdentity])]): DataFrame = {
    val nearNeighborsPersistable = nearNeighbors.map(x => NearNeighborsPersistable(x._1, x._2))
    RDDUtils.toDataFrame(spark, nearNeighborsPersistable)
  }

  /**
   * Creates a DataFrame from an RDD of XPMModel objects.
   *
   * The returned DataFrame is the exact format rquried for use with the
   * StateCalculationHydrator.
   *
   * It's structure should not be modified before being written out by the
   * Hydrator.
   *
   * @param spark A SparkSession
   * @param xpms An RDD of XPMModel objects
   * @return A DataFrame for use with the StateCalculationHydrator hydrate method.
   */
  def forStateCalculator(spark: SparkSession, xpms: RDD[XpmModel]): DataFrame = {
    // Note that this isn't necessarily the best way of doing this, but we need to keep the same output
    // as the old oxygen used to produce for the time being.
    // todo: consider changing this so that the XPM is written directly although
    // this will result in a slightly different avro output
    val persistableXPMs: RDD[Map[String, Any]] = xpms.map(x => x.data + ("pin" -> x.pin))
    RDDUtils.toDataFrame(spark, persistableXPMs)
  }
}
