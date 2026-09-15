package com.hoangluongtran0309.releaseflow.audience;

public class AudienceNotFoundException extends RuntimeException {

    public AudienceNotFoundException() {
        super("Audience was not found.");
    }
}
