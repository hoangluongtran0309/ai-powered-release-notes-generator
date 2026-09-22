package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ChangeNotReleasableException extends LocalizedException {

    public ChangeNotReleasableException() {
        super("error.change_not_releasable");
    }
}
