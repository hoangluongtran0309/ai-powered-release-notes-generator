package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

public class ProjectNotFoundException extends LocalizedException {

    public ProjectNotFoundException() {
        super("error.project_not_found");
    }
}
