package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/**
 * One response for every unusable token, so callers cannot tell a wrong token from an
 * expired, revoked, or already accepted one.
 */
public class InvalidInvitationException extends LocalizedException {

    public InvalidInvitationException() {
        super("error.invitation_invalid");
    }
}
