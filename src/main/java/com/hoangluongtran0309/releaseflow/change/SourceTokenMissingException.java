package com.hoangluongtran0309.releaseflow.change;

public class SourceTokenMissingException extends RuntimeException {

    public SourceTokenMissingException() {
        super("Add an access token to this repository before importing its history.");
    }
}
