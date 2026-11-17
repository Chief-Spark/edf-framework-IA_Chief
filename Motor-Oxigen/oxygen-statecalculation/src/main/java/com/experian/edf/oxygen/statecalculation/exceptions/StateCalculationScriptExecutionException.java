package com.experian.edf.oxygen.statecalculation.exceptions;

import java.io.Serializable;

public class StateCalculationScriptExecutionException extends Exception implements Serializable {
    public StateCalculationScriptExecutionException() {
    }

    public StateCalculationScriptExecutionException(String message) {
        super(message);
    }

    public StateCalculationScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public StateCalculationScriptExecutionException(Throwable cause) {
        super(cause);
    }
}
