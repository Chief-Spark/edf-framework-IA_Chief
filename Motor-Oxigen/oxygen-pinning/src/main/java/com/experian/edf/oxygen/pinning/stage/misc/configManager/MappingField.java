package com.experian.edf.oxygen.pinning.stage.misc.configManager;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MappingField implements Serializable {
    @JsonProperty("fieldName")
    private String fieldName;
    @JsonProperty("paramters")
    private FieldParamters paramters;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

}
