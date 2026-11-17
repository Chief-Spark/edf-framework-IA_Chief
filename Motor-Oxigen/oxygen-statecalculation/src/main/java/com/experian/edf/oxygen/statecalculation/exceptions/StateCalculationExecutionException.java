package com.experian.edf.oxygen.statecalculation.exceptions;

import java.io.Serializable;

public class StateCalculationExecutionException extends Exception implements Serializable {
    public StateCalculationExecutionException() {
    }

    public StateCalculationExecutionException(String message) {
        super(message);
    }

    public StateCalculationExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public StateCalculationExecutionException(Throwable cause) {
        super(cause);
    }
}
