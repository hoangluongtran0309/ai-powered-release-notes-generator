package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvitationEmailUnavailableException extends LocalizedException {

    public InvitationEmailUnavailableException() {
        super("error.invitation_email_unavailable");
    }
}
