package com.hoangluongtran0309.releaseflow.account;

public class InvitationAlreadyPendingException extends RuntimeException {

    public InvitationAlreadyPendingException() {
        super("This email address already has a pending invitation. Reissue or revoke it instead.");
    }
}
