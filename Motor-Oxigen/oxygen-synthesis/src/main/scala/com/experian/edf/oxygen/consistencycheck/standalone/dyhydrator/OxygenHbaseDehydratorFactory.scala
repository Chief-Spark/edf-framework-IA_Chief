package com.experian.edf.oxygen.consistencycheck.standalone.dyhydrator

import com.experian.edf.hydrator.core.compression.{NoOpCompressor, SnappyCompressor}
import com.experian.edf.hydrator.core.converter.Converter
import com.experian.edf.hydrator.core.converter.tostring.GenericToStringConverter
import com.experian.edf.hydrator.core.decoders.Decoder
import com.experian.edf.hydrator.core.serde.{AvroRecordSerDe, StringSerDe}
import com.experian.edf.hydrator.core.services.DehydratorService
import com.experian.edf.hydrator.hbase.config.HbaseHydratorConnectionConfig.RealConnection
import com.experian.edf.hydrator.hbase.config.{HbaseKeyValueTableConfig, HbaseRecordSourceConfig}
import com.experian.edf.hydrator.hbase.factories.{HbaseHydratorConnectionFactory, HbaseRecordSourceFactory}
import com.experian.edf.hydrator.hbase.recordsource.HbaseRecordSource
import com.experian.edf.hydrator.hbase.schemarepositories.HbaseAvroSchemaRepository
import com.experian.edf.oxygen.dataaccess.OxygenDataAccess
import com.experian.edf.oxygen.hydrator.core.Constants
import org.apache.avro.generic.GenericData.Record
import org.apache.hadoop.hbase.TableName

abstract class OxygenHbaseDehydratorFactory[DehydratedType, DehydratorType <: DehydratorService[DehydratedType]] {

  val TSV_IMPORT_LOAD = "tsvimport"
  val HYDRATOR_LOAD = "hydrator"
  val DEFAULT_COLUMN_FAMILY = "cf"
  val DEFAULT_COLUMN_QUALIFIER ="data"

  protected def makeAvroConverter(): Converter[Record, DehydratedType]

  protected def makeTextConverter(): Converter[String, DehydratedType]

  protected def makeToStringConverter(): Converter[DehydratedType, String] = {
    new GenericToStringConverter().asInstanceOf[Converter[DehydratedType, String]]
  }

  protected def makeDehydratorService[RecordType] (
    recordSource: HbaseRecordSource,
    decoderFactory: () => Decoder[Array[Byte], RecordType],
    converter: Converter[RecordType, DehydratedType],
    toStringConverter: Converter[DehydratedType, String]
  ): DehydratorType

  def make(
    dataAccess: OxygenDataAccess,
    fqTableName: String,
    loadMechanism: String,
    columnFamily: String = DEFAULT_COLUMN_FAMILY,
    columnQualifier: String = DEFAULT_COLUMN_QUALIFIER
  ): DehydratorType = {
    val conn = HbaseHydratorConnectionFactory.make(dataAccess)
    val tn = TableName.valueOf(fqTableName)
    val namespace = tn.getNamespaceAsString
    val tableName = tn.getQualifierAsString
    val recordSource = HbaseRecordSourceFactory.make(HbaseRecordSourceConfig(
      RealConnection(conn),
      HbaseKeyValueTableConfig(namespace, tableName, columnFamily, columnQualifier)
    ))
    loadMechanism match {
      case HYDRATOR_LOAD =>
        makeDehydratorService[Record](
          recordSource,
          () => {
            val schemaRepository = new HbaseAvroSchemaRepository(conn, namespace, tableName, columnFamily, columnQualifier)
            new Decoder[Array[Byte], Record](
              new AvroRecordSerDe(schemaRepository.getSchema(Constants.HBASE_SCHEMA_KEY)),
              new SnappyCompressor()
            )
          },
          makeAvroConverter(),
          makeToStringConverter()
        )
      case TSV_IMPORT_LOAD =>
        makeDehydratorService[String](
          recordSource,
          () => new Decoder[Array[Byte], String](new StringSerDe(), new NoOpCompressor()),
          makeTextConverter(),
          makeToStringConverter()
        )
      case _ =>
        throw new Exception(s"Invalid load mechanism, must be one of hydrator, tsvimport")
    }
  }
}
