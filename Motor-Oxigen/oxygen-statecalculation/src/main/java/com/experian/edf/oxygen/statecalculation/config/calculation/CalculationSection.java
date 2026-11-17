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
public class CalculationSection implements Serializable {
    @JsonPropertyDescription("Name of the section(This becomes the fieldname)")
    private final String name;
    @JsonPropertyDescription("Description of Calculated Section - Just for readability")
    private final String description;
    @JsonProperty(required = true)
    private final String type;
    @JsonPropertyDescription("List of fields to be selected")
    private final List<String> select;
    @JsonPropertyDescription("Define Groupby fields")
    private List<String> groupBy;
    @JsonPropertyDescription("Where condition to filter the records")
    private final String where;
    @JsonPropertyDescription("Link an another section to this section")
    private final List<Contains> contains;
    @JsonPropertyDescription("Determines if a fields needs to be included in aggregation output. \n Defaulted to false")
    @JsonProperty(value = "transient")
    private final Boolean isTransient = false;
    @JsonPropertyDescription("Is this section enabled for computation.\n Defaulted to true")
    private final Boolean enabled = true;
    @JsonPropertyDescription("Specify handler methods")
    private final CustomHandler handler;
    @JsonPropertyDescription("Number of past periods reported that needs to be considered")
    private final Integer numberOfPeriods;
    @JsonPropertyDescription("How the value has to be returned. For VALUE type only 1 field can be provided in select statement.")
    @JsonProperty(value = "return")
    private final CalculationReturn returnAs = CalculationReturn.KEY_VALUE;
    @JsonPropertyDescription("Define aggregates that needs to be computed")
    private final List<AggregatesConfiguration> aggregates;
    @JsonPropertyDescription("Custom Configuration for registered Types")
    private PluggableConfiguration plugin;

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

    public List<String> groupBy() {
        return groupBy;
    }

    public CalculationSection groupBy(List<String> groupBy) {
        this.groupBy = groupBy;
        return this;
    }

    public String where() {
        return where;
    }

    public List<Contains> contains() {
        return contains;
    }

    public Boolean isTransient() {
        return isTransient;
    }

    public Boolean enabled() {
        return enabled;
    }

    public CustomHandler handler() {
        return handler;
    }

    public Integer numberOfPeriods() {
        return numberOfPeriods;
    }

    public CalculationReturn returnAs() {
        return returnAs;
    }

    public List<AggregatesConfiguration> aggregates() {
        return aggregates;
    }

    public PluggableConfiguration plugin() {
        return plugin;
    }

    public CalculationSection plugin(PluggableConfiguration plugin) {
        this.plugin = plugin;
        return this;
    }
}
