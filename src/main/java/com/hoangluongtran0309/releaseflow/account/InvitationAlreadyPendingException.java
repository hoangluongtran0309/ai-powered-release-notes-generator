package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvitationAlreadyPendingException extends LocalizedException {

    public InvitationAlreadyPendingException() {
        super("error.invitation_already_pending");
    }
}
