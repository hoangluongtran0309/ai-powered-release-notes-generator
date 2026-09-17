package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.jira.JiraApiClient;
import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import com.hoangluongtran0309.releaseflow.project.SourceAccess;
import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asks the Project's issue tracker what it knows about a change from another source. This
 * is the one place a change is enriched from a source that is not its own, so it looks the
 * tracker up by type rather than by ID.
 */
@Component
class LinkedContextEnricher {

    private final SourceAccess sourceAccess;
    private final JiraApiClient jiraApiClient;

    LinkedContextEnricher(SourceAccess sourceAccess, JiraApiClient jiraApiClient) {
        this.sourceAccess = sourceAccess;
        this.jiraApiClient = jiraApiClient;
    }

    LinkedContext enrich(
            UUID organizationId,
            UUID projectId,
            MergedPullRequest change,
            SourceEnricher enricher,
            SourceCredentials credentials,
            String token
    ) {
        // A tracker's own change is already the issue; it links to nothing.
        if (!credentials.sourceType().hasWebhook() || credentials.sourceType() == SourceType.LINEAR) {
            return LinkedContext.NOT_SUPPORTED;
        }
        Optional<SourceCredentials> jira = sourceAccess.findByType(organizationId, projectId, SourceType.JIRA);
        Optional<String> jiraToken = jira.flatMap(SourceCredentials::accessToken);
        if (jira.isEmpty() || jiraToken.isEmpty()) {
            return LinkedContext.NOT_CONFIGURED;
        }

        // Most keys live in commit messages, so a list ReleaseFlow could not read may hide one.
        Optional<List<String>> commits = enricher.commitMessages(credentials, token, change);
        List<String> evidence = new ArrayList<>();
        evidence.add(change.title());
        evidence.add(change.description());
        evidence.add(change.targetBranch());
        commits.ifPresent(evidence::addAll);

        List<String> keys = JiraKeys.extract(jira.get().externalProjectKey(), evidence);
        if (keys.isEmpty()) {
            return commits.isPresent()
                    ? LinkedContext.of(LinkedContextStatus.NOT_FOUND, List.of())
                    : LinkedContext.retryable(LinkedContextStatus.PARTIAL, List.of());
        }

        List<LinkedIssue> issues = new ArrayList<>();
        for (String key : keys) {
            jiraApiClient
                    .issue(jira.get().apiBaseUrl(), key, jira.get().credentialIdentity(), jiraToken.get())
                    .ifPresent(issues::add);
        }
        if (issues.size() != keys.size()) {
            return LinkedContext.retryable(LinkedContextStatus.UNAVAILABLE, issues);
        }
        return commits.isPresent()
                ? LinkedContext.of(LinkedContextStatus.COLLECTED, issues)
                : LinkedContext.retryable(LinkedContextStatus.PARTIAL, issues);
    }
}
