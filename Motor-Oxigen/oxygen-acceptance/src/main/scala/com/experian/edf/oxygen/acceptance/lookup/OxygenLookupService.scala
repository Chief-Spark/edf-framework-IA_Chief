package com.experian.edf.oxygen.acceptance.lookup

import com.experian.edf.oxygen.context.{AppContext, SparkOxygenAppContext}
import com.experian.edf.oxygen.lookup.OxygenLookup
import org.apache.spark.broadcast.Broadcast
import org.slf4j.LoggerFactory.getLogger

/**
 *
 * @author annapurna - C22603A
 *         Created on 3/17/20 at 4:26 PM
 */

object OxygenLookupService {
  @transient lazy val logger = getLogger(this.getClass)

  private[this] var _service: Broadcast[OxygenLookup] = null

  def create[T <: AppContext](appContext: T): OxygenLookup = {
    if (_service == null) {
      val sparkCtx = appContext.asInstanceOf[SparkOxygenAppContext]
      logger.debug("Broadcasting lookup service")
      _service = sparkCtx.sc.broadcast(new SparkOxygenLookup(sparkCtx))
      logger.debug("Successfully broadcast lookup service -> " + _service.value.toString)
    }
    // Return the service
    _service.value
  }

  private[oxygen] def getServiceB: Broadcast[OxygenLookup] = _service

  def getService: OxygenLookup = if (_service == null) null else _service.value


}
