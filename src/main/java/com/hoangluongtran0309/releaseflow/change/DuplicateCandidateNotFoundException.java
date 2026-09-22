package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class DuplicateCandidateNotFoundException extends LocalizedException {

    public DuplicateCandidateNotFoundException() {
        super("error.duplicate_candidate_not_found");
    }
}
