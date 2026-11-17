package com.experian.edf.skills.validation;

/** El source-spec no cumple el esquema o una regla semántica. */
public class SpecError extends RuntimeException {

    public SpecError(String message) {
        super(message);
    }
}
