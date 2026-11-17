package com.experian.edf.oxygen.curation.framework;

import java.io.Serializable;

public interface InformationType extends Serializable {

    default String curationType() {
        return InformationType.class.getCanonicalName();
    }

}
