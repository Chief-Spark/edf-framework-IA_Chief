package com.experian.edf.oxygen.statecalculation.config.calculation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 2019-04-17 at 10:13
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CustomHandler implements Serializable {
    @JsonPropertyDescription("Fully qualified class name for Handling Custom Operation")
    @JsonProperty(value = "class")
    private String clazz;
    @JsonPropertyDescription("Lookup keys that has to be passed to Custom class")
    private List<String> lookups;

    public String clazz() {
        return clazz;
    }

    public List<String> lookups() {
        return lookups;
    }
}
