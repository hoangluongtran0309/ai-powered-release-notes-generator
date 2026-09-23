package com.hoangluongtran0309.releaseflow.account;

import java.util.Locale;

final class EmailAddress {

    private EmailAddress() {
    }

    static String canonicalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
