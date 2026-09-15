package com.hoangluongtran0309.releaseflow.change;

public class DuplicateCandidateDecidedException extends RuntimeException {

    public DuplicateCandidateDecidedException() {
        super("This possible duplicate has already been decided.");
    }
}
