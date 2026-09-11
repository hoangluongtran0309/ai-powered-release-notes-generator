package com.hoangluongtran0309.releaseflow.change;

public class ChangeNotFoundException extends RuntimeException {

    public ChangeNotFoundException() {
        super("Change was not found.");
    }
}
