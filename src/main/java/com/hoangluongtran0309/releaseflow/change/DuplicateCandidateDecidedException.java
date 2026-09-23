package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class DuplicateCandidateDecidedException extends LocalizedException {

    public DuplicateCandidateDecidedException() {
        super("error.duplicate_candidate_decided");
    }
}
