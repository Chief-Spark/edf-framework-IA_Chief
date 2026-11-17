package com.experian.edf.oxygen.statecalculation.config.foundation;

import com.experian.edf.oxygen.global.domain.Datatype;
import com.experian.edf.oxygen.statecalculation.config.calculation.CustomHandler;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

/**
 * @author Abraham, Thomas - c15627a
 */
@Getter
@AllArgsConstructor
@Builder
public class FoundationField implements Serializable {
    @JsonPropertyDescription("Name of Foundation Field")
    private final String name;
    @JsonPropertyDescription("Description of Foundation Field")
    private final String description;
    @JsonPropertyDescription("Foundation Field Type. Defaulted to : DERIVED")
    private final FieldType type = FieldType.DERIVED;
    @JsonPropertyDescription("Expresion to derive value, if field type is CALCULATED")
    private final String value;
    @JsonPropertyDescription("Specify handler methods")
    private final CustomHandler handler;
    @JsonPropertyDescription("Default value if no value is found in underlying synthetic event")
    private final String defaultValue;
    @JsonPropertyDescription("Is this field nullable(used for generating Schema). Defaulted to true")
    private final Boolean nullable = true;
    @JsonPropertyDescription("DataType of the Field. Defaulted to STRING")
    private final Datatype dataType = Datatype.STRING;
    @JsonPropertyDescription("Aliases names of this field. Functionality disabled by default")
    private final List<String> aliases;

    /**
     * Getter method for name
     *
     * @return Returns the value for name
     */
    public String name() {
        return name;
    }

    /**
     * Getter method for description
     *
     * @return Returns the value for description
     */
    public String description() {
        return description;
    }

    /**
     * Getter method for fieldType
     *
     * @return Returns the value for fieldType
     */
    public FieldType type() {
        return type;
    }

    /**
     * Getter method for value
     *
     * @return Returns the value for value
     */
    public String value() {
        return value;
    }

    /**
     * Getter method for handler
     *
     * @return Returns the value for handler
     */
    public CustomHandler handler() {
        return handler;
    }

    /**
     * Getter method for defaultValue
     *
     * @return Returns the value for defaultValue
     */
    public String defaultValue() {
        return defaultValue;
    }

    /**
     * Getter method for dataType
     *
     * @return Returns the value for dataType
     */
    public Datatype dataType() {
        return dataType;
    }

    /**
     * Getter method for aliases
     *
     * @return Returns the value for aliases
     */
    public List<String> aliases() {
        return aliases;
    }

    public Boolean nullable() {
        return nullable;
    }
}