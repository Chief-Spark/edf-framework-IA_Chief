package com.experian.edf.oxygen.statecalculation.config.calculation;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 2019-05-15 at 16:08
 */
public class PluggableConfiguration implements Serializable {

    @JsonIgnore
    private Map<String, Object> additionalConfiguration = new HashMap<String, Object>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalConfiguration() {
        return additionalConfiguration;
    }

    @JsonAnySetter
    public void setAdditionalConfiguration(String name, Object value) {
        this.additionalConfiguration.put(name, value);
    }
}
