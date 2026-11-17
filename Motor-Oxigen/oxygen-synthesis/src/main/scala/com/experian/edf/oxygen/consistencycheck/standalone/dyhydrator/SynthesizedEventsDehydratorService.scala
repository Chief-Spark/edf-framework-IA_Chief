package com.experian.edf.oxygen.consistencycheck.standalone.dyhydrator

import com.experian.edf.hydrator.core.converter.Converter
import com.experian.edf.hydrator.core.decoders.Decoder
import com.experian.edf.hydrator.core.services.{DehydratorService, GenericDehydratorService}
import com.experian.edf.hydrator.hbase.recordsource.HbaseRecordSource
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.configuration.configs.OxygenConfig
import com.experian.edf.oxygen.domain.JacksonPeriodUnitSerde
import com.experian.edf.oxygen.domain.SyntheticEvent
import com.experian.edf.oxygen.utils.{JsonUtils, ScalaJacksonUtil}
import org.apache.avro.generic.GenericData.Record

case class SynthesizedEventsPersistable(pin: Long, synthesizedEvents: List[SyntheticEvent]) extends Serializable

trait SynthesizedEventsDehydratorService extends DehydratorService[Array[SyntheticEvent]] {
  override val empty: Array[SyntheticEvent] = Array.empty[SyntheticEvent]
}

object SynthesizedEventsDehydratorService extends OxygenHbaseDehydratorFactory[Array[SyntheticEvent], SynthesizedEventsDehydratorService] {

  override protected def makeDehydratorService[RecordType](
    recordSource: HbaseRecordSource,
    decoderFactory: () => Decoder[Array[Byte], RecordType],
    converter: Converter[RecordType, Array[SyntheticEvent]],
    toStringConverter: Converter[Array[SyntheticEvent], String]
  ): SynthesizedEventsDehydratorService = {
    new GenericDehydratorService[Array[SyntheticEvent], Array[Byte], RecordType](
      recordSource, decoderFactory, converter, toStringConverter
    ) with SynthesizedEventsDehydratorService
  }

  override protected def makeAvroConverter(): Converter[Record, Array[SyntheticEvent]] = {
    new Converter[Record, Array[SyntheticEvent]] {
      override def convert(in: Record): Array[SyntheticEvent] = {
        val objectMapper = ScalaJacksonUtil.createObjectMapper
        JacksonPeriodUnitSerde.registerWithObjectMapper(objectMapper)
        objectMapper.readValue(in.toString, classOf[SynthesizedEventsPersistable]).synthesizedEvents.toArray
      }
    }
  }

  override protected def makeTextConverter(): Converter[String, Array[SyntheticEvent]] = {
    new Converter[String, Array[SyntheticEvent]] {
      override def convert(in: String): Array[SyntheticEvent] = {
        // todo: It would be nicer to use the ScalaJacksonUtils, but doing so seems
        // to cause a slight discrepency in how accOpeningDate is resolved
        // according to the "Can run high frequency synthesis with new event in a previous period but new event is younger for accOpeningDate EUC"
        // test of HighFrequencyEventSynthesizerTest class which fails when the below
        // lines are used instead.
        // The actual approach we are using is what we were originally using to
        // convert records from HBase before hydrator.
        // val objectMapper = ScalaJacksonUtil.createObjectMapper
        // JacksonPeriodUnitSerde.registerWithObjectMapper(objectMapper)
        // objectMapper.readValue(data, classOf[Array[SyntheticEvent]])
        JsonUtils.fromJson(in, classOf[Array[SyntheticEvent]])
      }
    }
  }

  def getHydratedTableName(oxygenConfig: OxygenConfig): String = {
    oxygenConfig.configMap(Constants.storesConfigKey.SYNTHETIC_EVENT_BATCH_FQ)
  }

  def getTableLoadMechanism(oxygenConfig: OxygenConfig): String = {
    oxygenConfig.configMap(Constants.storesConfigKey.SYNTHETIC_EVENT_BATCH_LOAD_MECHANISM)
  }
}