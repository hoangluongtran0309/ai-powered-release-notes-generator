package com.hoangluongtran0309.releaseflow.gitlab;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class GitLabHostNotAllowedException extends LocalizedException {

    public GitLabHostNotAllowedException(String messageKey, Object... arguments) {
        super(messageKey, arguments);
    }
}
