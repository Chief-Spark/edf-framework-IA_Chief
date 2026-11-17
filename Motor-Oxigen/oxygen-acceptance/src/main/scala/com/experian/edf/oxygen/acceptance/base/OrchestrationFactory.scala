package com.experian.edf.oxygen.acceptance.base

import com.experian.edf.oxygen.acceptance.engines.file.MarkerOrchestrator
import com.experian.edf.oxygen.base.Constants
import com.experian.edf.oxygen.orchestration.api.ControllerOrchestrator
import com.experian.edf.oxygen.orchestration.{OrchestrationContext, OxygenOrchestrator}
import org.slf4j.LoggerFactory.getLogger

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 2019-07-08 at 08:36
 */
object OchestrationFactory {

  @transient lazy val logger = getLogger(this.getClass)

  def create(orchestrationContext: OrchestrationContext, oxygenService: OxygenService): OxygenOrchestrator = {
    oxygenService.oxygenConf.oxygen.PROCESS_MANAGED_BY.toUpperCase match {
      case Constants.orchestratedBy.MARKERS => new MarkerOrchestrator(orchestrationContext, oxygenService)
      case Constants.orchestratedBy.CONTROLLER => new ControllerOrchestrator(orchestrationContext)
      case _ =>
        throw new UnsupportedOperationException(s"Orchestration Type: ${oxygenService.oxygenConf.oxygen.PROCESS_MANAGED_BY} not supported for key oxygen.application.processManagement.managedBy. Supported values are '${Constants.orchestratedBy.MARKERS} and ${Constants.orchestratedBy.CONTROLLER}'. ")
    }
  }
}
