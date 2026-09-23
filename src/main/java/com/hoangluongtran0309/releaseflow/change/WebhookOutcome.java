package com.hoangluongtran0309.releaseflow.change;

import java.util.Locale;

enum WebhookOutcome {
    PONG,
    RECORDED,
    DUPLICATE,
    IGNORED;

    String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
