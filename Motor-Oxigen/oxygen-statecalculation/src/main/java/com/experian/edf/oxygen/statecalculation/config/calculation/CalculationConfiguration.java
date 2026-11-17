package com.experian.edf.oxygen.statecalculation.config.calculation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Abraham, Thomas - c15627a
 */
public class CalculationConfiguration implements Serializable {
    @JsonPropertyDescription("Domain to which this configuration applies for")
    @JsonProperty(required = true)
    private final String domain;
    @JsonPropertyDescription("Different Aggregation Sections in state calculation")
    private final List<CalculationSection> calculations;
    @JsonPropertyDescription("Reference sections used within actual calculations")
    private final List<ReferenceSection> references;


    public CalculationConfiguration(String domain, List<CalculationSection> calculations, List<ReferenceSection> references) {
        this.domain = domain;
        this.calculations = calculations;
        this.references = references;
    }

    public CalculationConfiguration(String domain) {
        this.domain = domain;
        this.calculations = new ArrayList<>();
        this.references = new ArrayList<>();
    }

    public String domain() {
        return domain;
    }

    public List<CalculationSection> calculations() {
        return calculations;
    }

    public String getDomain() {
        return domain;
    }

    public List<CalculationSection> getCalculations() {
        return calculations;
    }


    public List<ReferenceSection> references() {
        return references;
    }

    public List<ReferenceSection> getReferences() {
        return references;
    }
}