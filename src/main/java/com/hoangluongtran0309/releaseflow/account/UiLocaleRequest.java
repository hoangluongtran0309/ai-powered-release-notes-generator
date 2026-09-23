package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.Size;

public class UiLocaleRequest {

    /** Null or blank means the browser decides, which is also what a new account does. */
    @Size(max = 16, message = "{validation.uiLocale.tooLong}")
    private String uiLocale;

    public String getUiLocale() {
        return uiLocale;
    }

    public void setUiLocale(String uiLocale) {
        this.uiLocale = uiLocale == null || uiLocale.isBlank() ? null : uiLocale.strip();
    }
}
