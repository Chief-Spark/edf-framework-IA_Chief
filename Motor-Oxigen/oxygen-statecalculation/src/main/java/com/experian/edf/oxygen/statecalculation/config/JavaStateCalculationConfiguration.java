package com.experian.edf.oxygen.statecalculation.config;


import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.CalculationType;
import com.experian.edf.oxygen.statecalculation.calculators.calculated.types.aggregates.functions.AggregateFunction;
import com.experian.edf.oxygen.statecalculation.processors.defaults.DefaultFoundationStateEnrichmentProcessor;
import com.experian.edf.oxygen.statecalculation.processors.foundation.FoundationStateEnrichmentProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author c15627a - Abraham, Thomas
 */
public class JavaStateCalculationConfiguration {
    private FoundationStateEnrichmentProcessor foundationStateEnrichmentProcessor = new DefaultFoundationStateEnrichmentProcessor();
    private List<CalculationType> calculationTypes = new ArrayList<>();
    private List<AggregateFunction<?>> aggregateFunctions = new ArrayList<>();
    private Boolean computeAliasInFoundation = false;
    private Boolean addParentEventsInAggregation = true;

    public FoundationStateEnrichmentProcessor foundationStateEnrichmentProcessor() {
        return foundationStateEnrichmentProcessor;
    }

    public JavaStateCalculationConfiguration foundationStateEnrichmentProcessor(FoundationStateEnrichmentProcessor foundationStateEnrichmentProcessor) {
        this.foundationStateEnrichmentProcessor = foundationStateEnrichmentProcessor;
        return this;
    }

    public Boolean computeAliasInFoundation() {
        return computeAliasInFoundation;
    }

    public JavaStateCalculationConfiguration computeAliasInFoundation(Boolean computeAliasInFoundation) {
        this.computeAliasInFoundation = computeAliasInFoundation;
        return this;
    }

    public JavaStateCalculationConfiguration registerAggregateFunctions(AggregateFunction<?> aggregateFunction) {
        aggregateFunctions.add(aggregateFunction);
        return this;
    }

    public JavaStateCalculationConfiguration registerAggregationType(CalculationType calculationType) {
        calculationTypes.add(calculationType);
        return this;
    }

    public List<AggregateFunction<?>> aggregateFunctions() {
        return aggregateFunctions;
    }

    public List<CalculationType> aggregationTypes() {
        return calculationTypes;
    }

    public Boolean addParentEventsInAggregation() {
        return addParentEventsInAggregation;
    }

    public JavaStateCalculationConfiguration addParentEventsInAggregation(Boolean addParentEventsInAggregation) {
        this.addParentEventsInAggregation = addParentEventsInAggregation;
        return this;
    }
}
