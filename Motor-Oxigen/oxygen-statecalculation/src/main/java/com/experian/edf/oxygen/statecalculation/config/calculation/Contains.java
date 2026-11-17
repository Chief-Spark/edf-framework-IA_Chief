package com.experian.edf.oxygen.statecalculation.config.calculation;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * @author Abraham, Thomas - c15627a
 */
@Getter
@AllArgsConstructor
public class Contains implements Serializable {
    private final String name;
    private final String section;
    private final ContainsLinkedBy linkedBy = ContainsLinkedBy.AUTO;

    public String name() {
        return name;
    }

    public String section() {
        return section;
    }

    public ContainsLinkedBy linkedBy() {
        return linkedBy;
    }
}