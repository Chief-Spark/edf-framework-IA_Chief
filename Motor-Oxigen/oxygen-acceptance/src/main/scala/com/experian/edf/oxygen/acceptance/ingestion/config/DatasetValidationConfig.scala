package com.experian.edf.oxygen.acceptance.ingestion.config

//import com.experain.edf.oxygen.module.ingestion.validation.FieldRuleType
import com.experian.edf.oxygen.validator.ObjectError
import com.fasterxml.jackson.annotation.JsonProperty
import ingestion.validation.FieldRuleType

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2/27/21 at 12:42 PM
 */
case class DatasetValidationConfig(
                                    extractions: ExtractionConfigs,
                                    validations: ValidationConfigs,
                                    processor: Option[Class[_]],
                                    metrics: Option[CustomMetricsConfig]
                                  ) extends Serializable

case class CustomMetricsConfig(
                                cacheSum: Option[String],
                                cacheCount: Option[String],
                                custom: List[MetricsElement]
                              ) extends Serializable

case class MetricsElement(
                           name: String,
                           expression: String
                         ) extends Serializable

case class FileNameValidationConfig(
                                   rules: List[MetadataValidationRuleConfig],
                                   accept: Option[String]
                                   ) extends Serializable

case class HeaderValidationConfig(
                                   numberOfHeaderRows: Int,
                                   isHeader: RuleDefinitionConfig,
                                   validateHeader: MetadataValidationConfig
                                 ) extends Serializable

case class FooterValidationConfig(
                                   numberOfFooterRows: Int,
                                   isFooter: RuleDefinitionConfig,
                                   validateFooter: MetadataValidationConfig
                                 ) extends Serializable

case class MetadataValidationConfig(
                                   rules: List[MetadataValidationRuleConfig],
                                   accept: Option[String]
                                   ) extends Serializable

case class BodyValidationConfig(
                                 field: String,
                                 rules: List[FieldValidationRuleConfig]
                               ) extends Serializable

case class MetadataValidationRuleConfig(
                                         @JsonProperty(required = true) name: String,
                                         fieldRule: RuleDefinitionConfig,
                                         errorCode: Option[ObjectError]
                                       )

case class FieldValidationRuleConfig(
                                      @JsonProperty(required = true) name: String,
                                      fieldRule: RuleDefinitionConfig,
                                      errorCode: Option[ObjectError],
                                      qualityScores: Map[String, RuleDefinitionConfig]
                                    ) extends Serializable

case class RuleDefinitionConfig(
                                 `type`: FieldRuleType,
                                 definition: String
                               ) extends Serializable

case class DeDuplicationConfig(
                                enabled: Boolean,
                                key: RuleDefinitionConfig,
                                version: String
                              ) extends Serializable

case class PredicateConfig(
                            name: String,
                            value: RuleDefinitionConfig
                          ) extends Serializable

case class ContextualValidationConfig(
                                      qualification : RuleDefinitionConfig,
                                      enabled: Boolean,
                                      predicates: List[PredicateConfig]
                                     ) extends Serializable

case class HistoricValidationConfig(
                                     historicAttributeArgs: String,
                                     accept: Option[String]
                                   ) extends Serializable

case class ExtractionConfigs(dataSource: RuleDefinitionConfig,
                             subscriber: RuleDefinitionConfig,
                             subscriberRelation: RuleDefinitionConfig,
                             asOfDate: RuleDefinitionConfig,
                             receivedDate: RuleDefinitionConfig) extends Serializable

case class ValidationConfigs(fileName: FileNameValidationConfig,
                             @JsonProperty(defaultValue = "false") searchAllHeaderFooter: Boolean,
                             header: HeaderValidationConfig,
                             footer: FooterValidationConfig,
                             body: List[BodyValidationConfig],
                             deDuplication: Option[DeDuplicationConfig],
                             contextual: Option[ContextualValidationConfig],
                             historic: Option[HistoricValidationConfig])