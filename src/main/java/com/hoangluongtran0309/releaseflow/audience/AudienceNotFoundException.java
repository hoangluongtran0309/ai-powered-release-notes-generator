package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class AudienceNotFoundException extends LocalizedException {

    public AudienceNotFoundException() {
        super("error.audience_not_found");
    }
}
