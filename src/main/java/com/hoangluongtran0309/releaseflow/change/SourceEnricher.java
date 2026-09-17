package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;

import java.util.List;
import java.util.Optional;

/**
 * Asks one provider for everything it can add to a change: which files it touched, and,
 * for a provider that can restate the change itself, its current wording. Called outside
 * any transaction, and never allowed to answer "no files" when it could not ask.
 */
interface SourceEnricher {

    SourceType sourceType();

    Enrichment enrich(SourceCredentials credentials, String token, MergedPullRequest change);

    /**
     * The messages of the commits this change carries, read only to find the issue keys a
     * tracker can explain, and never stored. Empty means the provider has no commits; a
     * missing value means it has them and they could not be read.
     *
     * @return at most {@value #MAX_COMMIT_MESSAGES} messages
     */
    default Optional<List<String>> commitMessages(
            SourceCredentials credentials,
            String token,
            MergedPullRequest change
    ) {
        return Optional.of(List.of());
    }

    int MAX_COMMIT_MESSAGES = 250;
}
