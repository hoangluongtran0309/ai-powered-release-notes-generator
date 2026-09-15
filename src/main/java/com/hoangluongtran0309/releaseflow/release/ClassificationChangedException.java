package com.hoangluongtran0309.releaseflow.release;

public class ClassificationChangedException extends RuntimeException {

    public ClassificationChangedException() {
        super("This change's classification changed since you loaded it. Review the current classification again.");
    }
}
