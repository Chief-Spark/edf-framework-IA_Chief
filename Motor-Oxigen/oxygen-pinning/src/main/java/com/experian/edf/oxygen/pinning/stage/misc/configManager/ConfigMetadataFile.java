package com.experian.edf.oxygen.pinning.stage.misc.configManager;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ConfigMetadataFile implements Serializable {

    @JsonProperty("enable")
    private Boolean enable = true;
    @JsonProperty("version")
    private Double version = 1.0;
    @JsonProperty("identifyKeys")
    private List<String> identifyKeys = new ArrayList<>();
    @JsonProperty("fields")
    private List<MappingField> fields = new ArrayList<>();

    public String fieldRecordKey(MappingField field) {
        return identifyKeys
                .stream()
                .map(each -> field
                        .getParamters()
                        .getAdditionalProperties()
                        .getOrDefault(each, "")
                        .toString())
                .collect(Collectors.joining("_"));
    }



    public List<String> getIdentifyKeys() {
        return identifyKeys;
    }

    public List<MappingField> getFields() {
        return fields;
    }

    public Boolean getEnable() {
        return enable;
    }

    public void setEnable(Boolean enable) {
        this.enable = enable;
    }

    public Double getVersion() {
        return version;
    }

    public void setVersion(Double version) {
        this.version = version;
    }


    public void setIdentifyKeys(List<String> identifyKeys) {
        this.identifyKeys = identifyKeys;
    }

    public void setRecords(List<MappingField> fields) {
        this.fields = fields;
    }
}