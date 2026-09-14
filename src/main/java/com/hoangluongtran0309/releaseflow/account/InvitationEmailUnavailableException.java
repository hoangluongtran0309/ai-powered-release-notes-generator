package com.hoangluongtran0309.releaseflow.account;

public class InvitationEmailUnavailableException extends RuntimeException {

    public InvitationEmailUnavailableException() {
        super("An account already exists for this email address.");
    }
}
