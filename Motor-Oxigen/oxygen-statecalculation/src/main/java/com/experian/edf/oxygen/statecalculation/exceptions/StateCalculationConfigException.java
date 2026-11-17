package com.experian.edf.oxygen.statecalculation.exceptions;

import java.io.Serializable;

public class StateCalculationConfigException extends Exception implements Serializable {
    public StateCalculationConfigException() {
    }

    public StateCalculationConfigException(String message) {
        super(message);
    }

    public StateCalculationConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public StateCalculationConfigException(Throwable cause) {
        super(cause);
    }
}
