package com.hoangluongtran0309.releaseflow.audience;

public class InvalidAudienceTemplateException extends RuntimeException {

    public static final String INVALID = "template_invalid";
    public static final String NARRATIVES_PATH = "template_narratives_path";

    private final String code;

    InvalidAudienceTemplateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
