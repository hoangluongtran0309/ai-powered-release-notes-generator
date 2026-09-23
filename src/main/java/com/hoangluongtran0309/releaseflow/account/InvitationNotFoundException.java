package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class InvitationNotFoundException extends LocalizedException {

    public InvitationNotFoundException() {
        super("error.invitation_not_found");
    }
}
