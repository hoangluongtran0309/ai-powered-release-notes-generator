package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.GitHubApiClient;
import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** GitHub can list a pull request's files; it never restates the pull request itself. */
@Component
class GitHubSourceEnricher implements SourceEnricher {

    private final GitHubApiClient gitHubApiClient;

    GitHubSourceEnricher(GitHubApiClient gitHubApiClient) {
        this.gitHubApiClient = gitHubApiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GITHUB;
    }

    @Override
    public Enrichment enrich(SourceCredentials credentials, String token, MergedPullRequest change) {
        ChangedFiles files = gitHubApiClient.pullRequestFiles(
                credentials.repositoryOwner(),
                credentials.repositoryName(),
                change.number(),
                token
        );
        return Enrichment.of(files, change);
    }

    @Override
    public Optional<List<String>> commitMessages(
            SourceCredentials credentials,
            String token,
            MergedPullRequest change
    ) {
        return gitHubApiClient.pullRequestCommits(
                credentials.repositoryOwner(),
                credentials.repositoryName(),
                change.number(),
                token
        );
    }
}
