package com.experian.edf.skills.acceptance;

/** Error de plantilla o de valor: hueco sin resolver, bloque mal cerrado, valor nulo. */
public class TemplateError extends RuntimeException {

    public TemplateError(String message) {
        super(message);
    }
}
