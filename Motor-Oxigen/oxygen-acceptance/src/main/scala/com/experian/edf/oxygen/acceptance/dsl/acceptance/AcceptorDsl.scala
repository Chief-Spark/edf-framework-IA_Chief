package com.experian.edf.oxygen.acceptance.dsl.acceptance

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.acceptance.engines.context.SparkOxygenAppContext
import com.experian.edf.oxygen.base.OxygenOrchestratedService
import com.experian.edf.oxygen.context.{OxygenAppContext}
import org.slf4j.LoggerFactory._

object AcceptorDsl {
  def apply[T <: OxygenAppContext](appContext: T): AcceptorDsl = {
    create(appContext)
  }

  def create[T <: OxygenAppContext](appContext: T): AcceptorDsl = {
    new SparkAcceptorDsl(appContext.asInstanceOf[SparkOxygenAppContext])
  }
}

private[oxygen] abstract class AcceptorDsl(appContext: OxygenAppContext) extends OxygenOrchestratedService(appContext) {

  @transient lazy val logger = getLogger(this.getClass)

  @throws(classOf[OxygenException])
  def runInternal(): Unit

  def run(): Unit = this.runInternal()

}
