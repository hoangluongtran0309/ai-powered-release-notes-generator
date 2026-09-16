package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

@Component
class GitHubChangedFileCollector implements ChangedFileCollector {

    private final GitHubApiClient gitHubApiClient;

    GitHubChangedFileCollector(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GITHUB;
    }

    @Override
    public ChangedFiles collect(SourceCredentials credentials, String token, int pullRequestNumber) {
        return gitHubApiClient.pullRequestFiles(
                credentials.repositoryOwner(),
                credentials.repositoryName(),
                pullRequestNumber,
                token
        );
    }
}
