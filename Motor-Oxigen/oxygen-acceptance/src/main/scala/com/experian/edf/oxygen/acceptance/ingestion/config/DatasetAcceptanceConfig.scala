package com.experian.edf.oxygen.acceptance.ingestion.config

case class DatasetAcceptanceConfig(fileConditions: Array[FileCondition],
                                   lookupMapping: Option[LookupMapping],
                                   fileActions: AcceptanceActions,
                                   recordAcceptance: String = "true") extends Serializable

case class FileCondition(name: String,
                         expression: String,
                         reasonCode: String,
                         isLogged: Boolean = false,
                         result: Boolean) extends Serializable

case class LookupMapping(tableName: String,
                         codeColumn: String,
                         severityColumn: String) extends Serializable

case class AcceptanceActions(accept: String = "true",
                             manualReview: String = "",
                             reject: String = "false",
                             manualAccept: String = "",
                             manualReject: String = "") extends Serializable