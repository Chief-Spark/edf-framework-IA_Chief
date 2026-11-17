package com.experian.edf.oxygen.statecalculation.config.calculation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/**
 * @author Abraham, Thomas - c15627a
 */
@Getter
@AllArgsConstructor
public class ReferenceSection implements Serializable {
    @JsonPropertyDescription("Name of the section(This becomes the fieldname)")
    private final String name;
    @JsonPropertyDescription("Description of Calculated Section - Just for readability")
    private final String description;
    @JsonProperty(required = true)
    private final String type;
    @JsonPropertyDescription("List of fields to be selected")
    private final List<String> select;
    @JsonPropertyDescription("Reference Data")
    private final String reference;
    @JsonPropertyDescription("Where condition to filter the records")
    private final String where;
    @JsonPropertyDescription("Is this section enabled for computation.\n Defaulted to true")
    private final Boolean enabled = true;
    @JsonPropertyDescription("Number of past periods reported that needs to be considered")
    private final Integer numberOfPeriods;

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String type() {
        return type;
    }

    public List<String> select() {
        return select;
    }

    public String where() {
        return where;
    }

    public Boolean enabled() {
        return enabled;
    }

    public Integer numberOfPeriods() {
        return numberOfPeriods;
    }

    public String reference() {
        return reference;
    }
}
