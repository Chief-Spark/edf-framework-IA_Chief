package com.experian.edf.oxygen.validation.domain;



import com.experian.edf.oxygen.domain.internal.DomainData;
import com.experian.edf.oxygen.domain.internal.Payload;
import com.experian.edf.oxygen.domain.internal.events.QualityScore;
import com.experian.edf.oxygen.validator.internal.ObjectError;
import scala.Tuple4;
import scala.collection.immutable.HashMap;
import scala.collection.immutable.Map;

import java.io.Serializable;

public interface IValidationProcessor extends Serializable {

    default Payload enrichEventWithPayload(ValidatedEvent event) {
        return new Payload(new HashMap<>());
    }

    Tuple4<ValidatedEvent, DomainData, QualityScore[], ObjectError[]> createValidatedEvent(String rawRecord, Map<String, Object> metadata);

    default int outputEventType() {
        return 0;
    }

    default boolean isDirective() {
        return false;
    }

}