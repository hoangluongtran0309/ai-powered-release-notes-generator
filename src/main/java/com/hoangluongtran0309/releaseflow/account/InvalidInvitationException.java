package com.hoangluongtran0309.releaseflow.account;

/**
 * One response for every unusable token, so callers cannot tell a wrong token from an
 * expired, revoked, or already accepted one.
 */
public class InvalidInvitationException extends RuntimeException {

    public InvalidInvitationException() {
        super("This invitation link is invalid or has expired. Ask an administrator for a new one.");
    }
}
