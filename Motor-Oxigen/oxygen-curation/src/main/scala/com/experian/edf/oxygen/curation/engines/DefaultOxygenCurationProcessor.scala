package com.experian.edf.oxygen.curation.engines

import com.experian.edf.oxygen.curation.engines.event.DefaultOxygenCuratedEvent
import com.experian.edf.oxygen.domain.TaggedValidatedEvent
import com.experian.edf.oxygen.domain.internal.CuratedEvent
import com.experian.edf.oxygen.domain.processors.CurationProcessorContext
import com.experian.edf.oxygen.utils.ScalaJacksonUtil
import com.fasterxml.jackson.databind.node.ObjectNode

/**
  * The curation processor is the interface to be implemented
  * by the bureau implementation. This processor helps interpret
  * accepted events and curates them.
  *
  */
class DefaultOxygenCurationProcessor(context: CurationProcessorContext) extends CurationProcessor(context: CurationProcessorContext) {
  /**
   * Implementation specific method: To curate the event and return a curated event
   *
   * @param taggedAcceptedEvent AcceptedEvent : The quality scored (accepted) event which needs to be curated
   * @return CuratedEvent
   */
  override def curateEvent(taggedAcceptedEvent: TaggedValidatedEvent): CuratedEvent = {
    val validatedEventsAsMap = ScalaJacksonUtil.toJsonNode(taggedAcceptedEvent.validatedRecordJson)
      .getOrElse(ScalaJacksonUtil.createObjectNode)
      .asInstanceOf[ObjectNode]
      .put("oxygenCurationDefaultField","oxygenCurationDefaultValue")

    /*    val validatedEventsAsMap = OxygenCollectionsUtils.asJava(ScalaJacksonUtil.fromJson(taggedAcceptedEvent.validatedRecordJson, classOf[Map[String, Any]]).get)
    validatedEventsAsMap.put("oxygenCurationDefaultField","oxygenCurationDefaultValue")*/

    val curatedEventAsJson = ScalaJacksonUtil.toJson(validatedEventsAsMap).get
    val eventWithDefaultField = new DefaultOxygenCuratedEvent(curatedEventAsJson)
    eventWithDefaultField
  }
}

