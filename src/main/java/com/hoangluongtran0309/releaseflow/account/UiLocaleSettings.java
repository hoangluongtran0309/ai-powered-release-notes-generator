package com.hoangluongtran0309.releaseflow.account;

import java.util.List;

/**
 * @param uiLocale the language saved on the account, or null when the browser decides
 * @param supported the languages this deployment ships, each named in its own language
 */
public record UiLocaleSettings(String uiLocale, List<Option> supported) {

    public record Option(String tag, String displayName) {
    }
}
