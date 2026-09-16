package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.source.ChangedFiles;

/**
 * What a provider added to a change.
 *
 * @param files what it could say about the files the change touched
 * @param change the change, with any wording the provider restated
 * @param retryable whether asking again later may add what is missing
 */
record Enrichment(ChangedFiles files, MergedPullRequest change, boolean retryable) {

    static Enrichment of(ChangedFiles files, MergedPullRequest change) {
        return new Enrichment(files, change, files.retryable());
    }

    static Enrichment retryable(ChangedFiles files, MergedPullRequest change) {
        return new Enrichment(files, change, true);
    }
}
