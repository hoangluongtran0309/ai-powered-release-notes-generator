package com.hoangluongtran0309.releaseflow.account;

public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("Invitation was not found.");
    }
}
