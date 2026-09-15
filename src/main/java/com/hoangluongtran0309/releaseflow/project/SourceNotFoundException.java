package com.hoangluongtran0309.releaseflow.project;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException() {
        super("Source was not found.");
    }
}
