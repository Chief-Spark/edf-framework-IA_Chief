package com.experian.edf.oxygen.acceptance.ingestion

import com.fasterxml.jackson.annotation.JsonProperty
import ingestion.IngestionDatasetType

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2/25/21 at 12:43 AM
 */
case class IngestionMappers(mappers: List[IngestionMapper])

case class IngestionMapper(
                            @JsonProperty(required = true)
                            pattern: String,
                            datasetType: IngestionDatasetType = IngestionDatasetType.JSON,
                            parser: Option[String],
                            validation: Option[String],
                            acceptance: Option[String],
                            curation: Option[String]
                          )


