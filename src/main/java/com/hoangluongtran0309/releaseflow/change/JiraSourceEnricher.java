package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ChangedFiles;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

/**
 * A Jira issue has no files, and nothing to restate: the poll already read it straight
 * from Jira moments earlier.
 */
@Component
class JiraSourceEnricher implements SourceEnricher {

    @Override
    public SourceType sourceType() {
        return SourceType.JIRA;
    }

    @Override
    public Enrichment enrich(SourceCredentials credentials, String token, MergedPullRequest change) {
        return Enrichment.of(ChangedFiles.notSupported(), change);
    }
}
