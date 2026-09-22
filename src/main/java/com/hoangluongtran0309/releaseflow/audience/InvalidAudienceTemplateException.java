package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvalidAudienceTemplateException extends LocalizedException {

    public static final String INVALID = "template_invalid";
    public static final String NARRATIVES_PATH = "template_narratives_path";
    public static final String LANGUAGE_NOT_TARGETED = "template_language_not_targeted";

    private final String code;

    InvalidAudienceTemplateException(String code, String messageKey, Object... arguments) {
        super(messageKey, arguments);
        this.code = code;
    }

    static InvalidAudienceTemplateException languageNotTargeted(String language) {
        return new InvalidAudienceTemplateException(
                LANGUAGE_NOT_TARGETED,
                "error.template_language_not_targeted",
                language.strip()
        );
    }

    public String code() {
        return code;
    }
}
