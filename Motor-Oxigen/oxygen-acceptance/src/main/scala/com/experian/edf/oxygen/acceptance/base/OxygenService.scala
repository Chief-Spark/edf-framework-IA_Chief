package com.experian.edf.oxygen.acceptance.base

import com.experian.edf.oxygen.acceptance.lookup.OxygenLookupService
import com.experian.edf.oxygen.acceptance.util.OxygenFileUtils
import com.experian.edf.oxygen.common.messaging.KafkaOxygenMessaging
import com.experian.edf.oxygen.context.AppContext
import com.experian.edf.oxygen.global.utils.implicits.OxygenImplicits
//import com.experian.edf.oxygen.utils.OxygenFileUtils
//import com.experian.edf.oxygen.global.utils.implicits.OxygenImplicits
//import com.experian.edf.oxygen.lookup.OxygenLookupService
//import com.experian.edf.oxygen.utils.OxygenFileUtils


/**
 * All the different phases of the pipeline will extend the OxygenService which is the base abstraction.
 * It can also act as a container for some common fields / defs like fileUtils, configuration, etc.
 *
 * @param appContext
 */
private[oxygen] abstract class OxygenService(appContext: AppContext) extends Serializable {

  // Handle to the OxygenFileUtils.
  private[oxygen] val fileUtils = OxygenFileUtils.create(appContext)

  val oxygenImplicits: OxygenImplicits.type = OxygenImplicits


  // Handle to the oxygen configuration
  private[oxygen] val oxygenConf = appContext.config

  // Create the Lookup based on definitions in the configuration files
  OxygenLookupService.create(appContext)

  // This is the process manager which will manage the handling of files via Kafka / DB / Filesystem
  // @transient val orchestrator: OxygenOrchestrator = OchestrationFactory.create(this)

  // @transient private[oxygen] val executionGraph: OxygenExecutionGraph = OxygenExecutionGraphService.getOrCreate(this)


  val messaging = new KafkaOxygenMessaging(appContext)


  override def finalize(): Unit = {
    //    processManager.close()
    super.finalize()
  }
}
