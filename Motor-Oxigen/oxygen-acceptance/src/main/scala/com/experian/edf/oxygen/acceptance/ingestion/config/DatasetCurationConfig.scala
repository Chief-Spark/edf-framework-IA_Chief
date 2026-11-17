package com.experian.edf.oxygen.acceptance.ingestion.config

case class DatasetCurationConfig(curations : List[CurationFieldRule])

case class CurationFieldRule(
                              field: String,
                             `type`: String,
                              definition: String
                            )
