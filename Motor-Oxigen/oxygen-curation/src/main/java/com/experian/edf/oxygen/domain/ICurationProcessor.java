package com.experian.edf.oxygen.domain;

public interface ICurationProcessor {
    com.experian.edf.oxygen.domain.internal.CuratedEvent curateEvent(TaggedValidatedEvent taggedValidatedEvent);
}
