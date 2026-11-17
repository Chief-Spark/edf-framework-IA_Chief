package com.experian.edf.oxygen.statecalculation.config.foundation;

import java.io.Serializable;

/**
 * @author Abraham, Thomas - c15627a
 */
public enum FieldType implements Serializable {
    DERIVED,
    CALCULATED,
    CUSTOM_WITH_HISTORY,
    CUSTOM,
    @Deprecated
    UNKNOWN
}
