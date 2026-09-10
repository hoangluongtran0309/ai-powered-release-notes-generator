package com.hoangluongtran0309.releaseflow.project;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Project was not found.");
    }
}
