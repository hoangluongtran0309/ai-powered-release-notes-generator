package com.hoangluongtran0309.releaseflow.account;

import java.util.List;

/**
 * @param supported suggested choices; any valid language tag is accepted
 */
public record OutputLanguageSettings(String outputLanguage, String displayName, List<Option> supported) {

    public record Option(String tag, String displayName) {
    }
}
