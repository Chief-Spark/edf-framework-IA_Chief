package com.experian.edf.skills.curation;

/** El source-spec, las plantillas o el fixture no permiten generar los artefactos. */
public class RenderError extends RuntimeException {

    public RenderError(String message) {
        super(message);
    }
}
