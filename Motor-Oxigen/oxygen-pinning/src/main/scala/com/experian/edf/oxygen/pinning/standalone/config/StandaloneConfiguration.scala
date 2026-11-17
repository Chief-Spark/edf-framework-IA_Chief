package com.experian.edf.oxygen.pinning.standalone.config

import com.experian.edf.oxygen.domain.vle.VleSizeClassifier
import com.experian.edf.oxygen.pinning.stage.misc.enums.FS


/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/28/20 at 12:26 AM
 */
case class StandaloneConfiguration(standaloneApp: StandaloneConfig)

case class OxygenStandaloneConfiguration(oxygen: StandaloneConfiguration)

case class StandaloneConfig(
                      pipelineConfigs: List[String],
                      classifier: VleSizeClassifier,
                      metadataFile: String,
                      entity: String,
                      environment: String,
                      write: WriteOptions,
                      hdfsProperties: HdfsProperties,
                      paths: Paths,
                      kafka: Kafka,
                      hbase: HbaseProperties,
                      fileSystem: FS = FS.LOCAL,
                      s3: Option[S3Configuration] = Option.empty
                    ) extends Serializable

case class Paths(
                  appHome: String,
                  generationsHome: String,
                  outputHome:String
                )

case class Kafka(
                  enabled: Boolean,
                  triggerTopic: String,
                  consumerGroup: String
                )

case class WriteOptions(
                         xpmCalculated: Boolean = true,
                         xpmFoundation: Boolean = false,
                         synthesised: Boolean = false,
                         consistencyInfos: Boolean = false,
                         hc: Boolean = false
                       ) extends Serializable

case class HdfsAuthProperties(
                               enabled: Boolean = false,
                               principal: String = null,
                               keytab: String = null,
                               authenticationType: String = "Kerberos")

case class HdfsProperties(
                           auth: HdfsAuthProperties = HdfsAuthProperties(),
                           coreSite: String = "/etc/hadoop/conf.cloudera.hdfs/core-site.xml",
                           hdfsSite: String = "/etc/hadoop/conf.cloudera.hdfs/hdfs-site.xml",
                           defaultFs: String = "hdfs://nameservice1")

case class HbaseProperties(
                            namespace: String,
                            auth: HbaseAuthProperties,
                            tables: HbaseTables,
                            enabled: Boolean = false,
                            columnFamily: String = "cf",
                            qualifier: String = "q",
                            coreSite: String = "/etc/hadoop/conf.cloudera.hdfs/core-site.xml",
                            hbaseSite: String = "/etc/hbase/conf.cloudera.hbase/hbase-site.xml"
                          )

case class HbaseTables(vleState: String,
                       vleRealtime: String)

case class HbaseAuthProperties(
                                principal: String,
                                keytab: String,
                                enabled: Boolean = false,
                                authenticationType: String = "Kerberos"
                              )
case class S3Configuration(
                         bucketName: Option[String] = Option.empty,
                         region: Option[String] = Option.empty
                         )