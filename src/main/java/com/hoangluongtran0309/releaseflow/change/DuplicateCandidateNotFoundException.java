package com.hoangluongtran0309.releaseflow.change;

public class DuplicateCandidateNotFoundException extends RuntimeException {

    public DuplicateCandidateNotFoundException() {
        super("Possible duplicate was not found.");
    }
}
