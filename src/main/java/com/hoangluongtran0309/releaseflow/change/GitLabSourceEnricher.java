package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.gitlab.GitLabApiClient;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

/** GitLab can list a merge request's diffs; it never restates the merge request itself. */
@Component
class GitLabSourceEnricher implements SourceEnricher {

    private final GitLabApiClient gitLabApiClient;

    GitLabSourceEnricher(GitLabApiClient gitLabApiClient) {
        this.gitLabApiClient = gitLabApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GITLAB;
    }

    @Override
    public Enrichment enrich(SourceCredentials credentials, String token, MergedPullRequest change) {
        ChangedFiles files = gitLabApiClient.mergeRequestFiles(
                credentials.apiBaseUrl(),
                credentials.externalProjectKey(),
                change.number(),
                token
        );
        return Enrichment.of(files, change);
    }
}
