package com.experian.edf.oxygen.statecalculation.config.calculation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * @author Abraham, Thomas - c15627a
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupingClause implements Serializable {
    private List<String> groupBy;

    public List<String> groupBy() {
        return groupBy;
    }
}