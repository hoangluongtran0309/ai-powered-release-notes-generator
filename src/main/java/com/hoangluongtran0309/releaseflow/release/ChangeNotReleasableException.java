package com.hoangluongtran0309.releaseflow.release;

public class ChangeNotReleasableException extends RuntimeException {

    public ChangeNotReleasableException() {
        super("Only changes that have finished processing and are not in another release can be added.");
    }
}
