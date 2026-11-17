package com.experian.edf.oxygen.acceptance.engines

import com.experian.edf.oxygen.global.constants.logging.LegacyOxygenLoggingConstants
import com.experian.edf.oxygen.logging._

// @formatter:off
class LegacyAcceptedRecordsLoggingEvent(override val datasetId: String, acceptedRecords: Long) extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.ACCEPTANCE, datasetId, "acceptedrecords", acceptedRecords) with SummaryEvent

class LegacyRejectedRecordsLoggingEvent(override val datasetId: String, rejectedRecords: Long) extends DatasetMetricEvent(LegacyOxygenLoggingConstants.stages.ACCEPTANCE, datasetId, "rejectedrecords", rejectedRecords) with SummaryEvent


