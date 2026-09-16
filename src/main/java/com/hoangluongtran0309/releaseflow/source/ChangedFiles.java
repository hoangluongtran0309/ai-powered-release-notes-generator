package com.hoangluongtran0309.releaseflow.source;

import java.util.List;

/**
 * The outcome of asking a provider which files a change touched. An unavailable list is
 * never treated as empty: it means nothing could be checked. A provider that has no such
 * notion at all reports {@link #notSupported()}, which the rules answer differently.
 *
 * @param failure a short, stable reason code, or null when the files were collected
 * @param retryable whether asking again later may succeed
 */
public record ChangedFiles(List<ChangedFile> files, String failure, boolean retryable) {

    public static final String NO_ACCESS_TOKEN = "no_access_token";
    public static final String ACCESS_REJECTED = "access_rejected";
    public static final String TOO_MANY_FILES = "too_many_files";
    public static final String INVALID_RESPONSE = "invalid_response";
    public static final String GITHUB_UNAVAILABLE = "github_unavailable";
    public static final String GITLAB_UNAVAILABLE = "gitlab_unavailable";
    /** The provider has no notion of a changed file, so none could ever be listed. */
    public static final String NOT_SUPPORTED = "not_supported";
    /** No list was ever recorded for the change. */
    public static final String NOT_RECORDED = "not_recorded";
    /** The provider withheld a diff, so the file list cannot be trusted to be complete. */
    public static final String DIFF_UNAVAILABLE = "diff_unavailable";

    public ChangedFiles {
        files = files == null ? null : List.copyOf(files);
    }

    public static ChangedFiles collected(List<ChangedFile> files) {
        return new ChangedFiles(files, null, false);
    }

    public static ChangedFiles unavailable(String failure, boolean retryable) {
        return new ChangedFiles(null, failure, retryable);
    }

    /** An issue tracker cannot report files; that is not the same as failing to. */
    public static ChangedFiles notSupported() {
        return new ChangedFiles(null, NOT_SUPPORTED, false);
    }

    public boolean isCollected() {
        return files != null;
    }

    public boolean isNotSupported() {
        return NOT_SUPPORTED.equals(failure);
    }
}
