package com.experian.edf.oxygen.curation.engines

import com.experian.edf.global.exceptions.OxygenException
import com.experian.edf.oxygen.configuration.configs.typesafe.ProcessElement
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.utils.ScalaJacksonUtil.logger

/**
 *
 * @author Abraham, Thomas - C15627A
 *         Created on 10/30/19 at 8:07 PM
 */
object CuratorUtils {

  def getProcessorMap(patternToProcessorMapping: Map[String, ProcessElement], curationContext: CurationProcessorContext, enableDefaultProcessor: Boolean): Map[String, CurationProcessor] = {
    patternToProcessorMapping.map(mapping => try {
      val processElement = if (enableDefaultProcessor) classOf[DefaultOxygenCurationProcessor] else mapping._2.processor.get
      val constructor = processElement.getConstructor(classOf[CurationProcessorContext])
      (mapping._1, constructor.newInstance(curationContext).asInstanceOf[CurationProcessor])
    }
    catch {
      case e: Throwable =>
        logger.error(s"Unable to Cast ${mapping._2} to Processor - " + e.getMessage, e)
        throw new OxygenException(s"Unable to Cast ${mapping._2} to Processor", e)
    }
    )
  }
}
