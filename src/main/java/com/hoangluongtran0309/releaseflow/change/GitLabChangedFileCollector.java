package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.gitlab.GitLabApiClient;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

@Component
class GitLabChangedFileCollector implements ChangedFileCollector {

    private final GitLabApiClient gitLabApiClient;

    GitLabChangedFileCollector(GitLabApiClient gitLabApiClient) {
        this.gitLabApiClient = gitLabApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GITLAB;
    }

    @Override
    public ChangedFiles collect(SourceCredentials credentials, String token, int mergeRequestIid) {
        return gitLabApiClient.mergeRequestFiles(
                credentials.apiBaseUrl(),
                credentials.externalProjectKey(),
                mergeRequestIid,
                token
        );
    }
}
