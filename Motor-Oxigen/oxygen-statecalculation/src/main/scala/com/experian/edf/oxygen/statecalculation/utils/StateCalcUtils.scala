package com.experian.edf.oxygen.statecalculation.utils

import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.context.AppContext
import com.experian.edf.oxygen.utils.FileUtils
import org.slf4j.LoggerFactory.getLogger

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 1/7/20 at 10:10 AM
 */
class StateCalcUtils(fileUtils: FileUtils, appContext: AppContext) extends Serializable {
  @transient private lazy val logger = getLogger(this.getClass)

  def displayInfos: Unit = {
    if (logger.isInfoEnabled()) {
      val confs = appContext.config.oxygen
      val stateCalcConfigs = confs.stage.stateCalc
      logger.info("Source Directory: " + confs.stage.consistency.get.paths.consistencyDir)
      logger.info("Dataset Format Input: " + stateCalcConfigs.readWriteFormat.get.input.get)
      logger.info("Dataset Format Output: " + stateCalcConfigs.readWriteFormat.get.output.get)
      logger.info("Number of Foundation Files : " + stateCalcConfigs.paths.foundationConfigurations.length)
      logger.info("Foundation Files : " + stateCalcConfigs.paths.foundationConfigurations.mkString(", "))
      logger.info("Number of Calculation Files : " + stateCalcConfigs.paths.calculationConfigurations.length)
      logger.info("Calculation Files : " + stateCalcConfigs.paths.calculationConfigurations.mkString(", "))
      logger.info("Kafka Enabled : " + confs.KAFKA_ENABLED)
      logger.info("TSV Output Enabled: " + confs.isFeatureActive(Constants.features.STATECALC_OUTPUT_AS_TSV))
      logger.info("Calculation Output Enabled : " + confs.isFeatureActive(Constants.features.STATECALC_OUTPUT_CALCULATION))
      logger.info("Foundation Output Enabled : " + confs.isFeatureActive(Constants.features.STATECALC_OUTPUT_FOUNDATION))
      logger.info("Perform Calculation Enrichment : " + confs.isFeatureActive(Constants.features.STATECALC_PERFORM_CALCULATION_ENRICHMENT))
      logger.info("Attach Historic Data : " + confs.isFeatureActive(Constants.features.STATECALC_ATTACH_HISTORIC_DATA))
      logger.info("Additional Foundation Output : " + stateCalcConfigs.paths.additionalFoundationOutput)
    }
  }
}
