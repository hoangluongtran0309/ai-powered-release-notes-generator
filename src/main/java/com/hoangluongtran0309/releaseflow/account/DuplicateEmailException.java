package com.hoangluongtran0309.releaseflow.account;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("An account already exists for this email address.");
    }
}
