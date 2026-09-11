package com.hoangluongtran0309.releaseflow.release;

public class ChangeNotReleasableException extends RuntimeException {

    public ChangeNotReleasableException() {
        super("Only changes that no longer need review and are not in another release can be added.");
    }
}
