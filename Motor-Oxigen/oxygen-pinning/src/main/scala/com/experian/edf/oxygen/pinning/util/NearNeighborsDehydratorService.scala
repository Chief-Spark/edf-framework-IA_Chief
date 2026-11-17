package com.experian.edf.oxygen.pinning.util

import com.experian.edf.hydrator.core.converter.Converter
import com.experian.edf.hydrator.core.decoders.Decoder
import com.experian.edf.hydrator.core.services.{DehydratorService, GenericDehydratorService}
import com.experian.edf.hydrator.hbase.recordsource.HbaseRecordSource
import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.domain.TaggedIdentity
import com.experian.edf.oxygen.hydrator.core.constants.PinningConstants
import com.experian.edf.oxygen.hydrator.core.dehydrator.OxygenHbaseDehydratorFactory
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import org.apache.avro.generic.GenericData.Record

case class NearNeighborsPersistable(id: String, taggedEvents: Array[TaggedIdentity])

trait NearNeighborsDehydratorService extends DehydratorService[Array[TaggedIdentity]] {
  override val empty = Array.empty[TaggedIdentity]
}

object NearNeighborsDehydratorService extends OxygenHbaseDehydratorFactory[Array[TaggedIdentity], NearNeighborsDehydratorService] {

  override protected def makeDehydratorService[RecordType](
    recordSource: HbaseRecordSource,
    decoderFactory: () => Decoder[Array[Byte], RecordType],
    converter: Converter[RecordType, Array[TaggedIdentity]],
    toStringConverter: Converter[Array[TaggedIdentity], String]
  ): NearNeighborsDehydratorService = {
    new GenericDehydratorService[Array[TaggedIdentity], Array[Byte], RecordType](
      recordSource, decoderFactory, converter, toStringConverter
    ) with NearNeighborsDehydratorService
  }

  override protected def makeAvroConverter(): Converter[Record, Array[TaggedIdentity]] = {
    new Converter[Record, Array[TaggedIdentity]]() {
      override def convert(in: Record): Array[TaggedIdentity] = {
        ScalaJacksonUtil.fromJson[NearNeighborsPersistable](in.toString).get.taggedEvents
      }
    }
  }

  override protected def makeTextConverter(): Converter[String, Array[TaggedIdentity]] = {
    new Converter[String, Array[TaggedIdentity]]() {
      override def convert(in: String): Array[TaggedIdentity] = {
        ScalaJacksonUtil.fromJson[Array[TaggedIdentity]](in).get
      }
    }
  }

  def getHydratedTableName(oxygenConfig: OxygenConfig): String = {
    oxygenConfig.configMap(PinningConstants.storesConfigKey.PINNING_NEAR_NEIGHBOR_STATE_FQ)
  }

  def getTableLoadMechanism(oxygenConfig: OxygenConfig): String = {
    oxygenConfig.configMap(PinningConstants.storesConfigKey.PINNING_NEAR_NEIGHBOR_STATE_LOAD_MECHANISM)
  }

}