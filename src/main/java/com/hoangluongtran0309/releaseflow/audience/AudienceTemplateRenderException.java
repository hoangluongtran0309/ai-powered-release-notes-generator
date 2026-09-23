package com.hoangluongtran0309.releaseflow.audience;

/** A stored template could not be rendered. Templates are validated on save, so this is rare. */
public class AudienceTemplateRenderException extends RuntimeException {

    AudienceTemplateRenderException(Throwable cause) {
        super("An audience template could not be rendered.", cause);
    }
}
