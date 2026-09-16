package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.SourceCredentials;
import com.hoangluongtran0309.releaseflow.source.SourceType;

/**
 * Asks one provider for everything it can add to a change: which files it touched, and,
 * for a provider that can restate the change itself, its current wording. Called outside
 * any transaction, and never allowed to answer "no files" when it could not ask.
 */
interface SourceEnricher {

    SourceType sourceType();

    Enrichment enrich(SourceCredentials credentials, String token, MergedPullRequest change);
}
