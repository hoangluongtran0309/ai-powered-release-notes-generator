package com.hoangluongtran0309.releaseflow.audience;

public class InvalidAudienceTemplateException extends RuntimeException {

    public static final String INVALID = "template_invalid";
    public static final String NARRATIVES_PATH = "template_narratives_path";
    public static final String LANGUAGE_NOT_TARGETED = "template_language_not_targeted";

    private final String code;

    InvalidAudienceTemplateException(String code, String message) {
        super(message);
        this.code = code;
    }

    static InvalidAudienceTemplateException languageNotTargeted(String language) {
        return new InvalidAudienceTemplateException(
                LANGUAGE_NOT_TARGETED,
                "\"" + language.strip() + "\" is not one of the Organization's release note languages."
        );
    }

    public String code() {
        return code;
    }
}
