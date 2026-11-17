package com.experian.edf.oxygen.statecalculation.config.calculation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author Abraham, Thomas - C15627A
 * Created on 2019-04-30 at 10:46
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AggregatesConfiguration implements Serializable {
    private String function;
    private String field;
    private String name;

    public String function() {
        return function;
    }

    public String field() {
        return field;
    }

    public String name() {
        if (name == null)
            name = field;
        return name;
    }

    public String getName() {
        return name();
    }

}