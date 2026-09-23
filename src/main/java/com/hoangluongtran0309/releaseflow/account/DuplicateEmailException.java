package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class DuplicateEmailException extends LocalizedException {

    public DuplicateEmailException() {
        super("error.email_already_registered");
    }
}
