package com.hoangluongtran0309.releaseflow.project;

public class GitHubRepositoryAlreadyConnectedException extends RuntimeException {

    public GitHubRepositoryAlreadyConnectedException() {
        super("This GitHub repository is already connected to another project.");
    }
}
