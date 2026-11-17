package com.experian.edf.oxygen.statecalculation.config.foundation;

import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Abraham, Thomas - c15627a
 */
@Getter
public class FoundationStateConfiguration implements Serializable {
    private final String domain;
    private final List<FoundationField> fields;

    public FoundationStateConfiguration(String domain, List<FoundationField> fields) {
        this.domain = domain;
        this.fields = fields;
    }

    public FoundationStateConfiguration(String domain) {
        this.domain = domain;
        this.fields = new ArrayList<>();
    }

    /**
     * Getter method for domain
     *
     * @return Returns the value for domain
     */
    public String domain() {
        return domain;
    }

    /**
     * Getter method for fields
     *
     * @return Returns the value for fields
     */
    public List<FoundationField> fields() {
        return fields;
    }
}