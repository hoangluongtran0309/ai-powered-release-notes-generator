package com.hoangluongtran0309.releaseflow.github;

import java.util.List;

/**
 * The outcome of asking GitHub which files a pull request changed. An unavailable
 * list is never treated as empty: it means nothing could be checked.
 *
 * @param failure a short, stable reason code, or null when the files were collected
 * @param retryable whether asking again later may succeed
 */
public record PullRequestFiles(List<ChangedFile> files, String failure, boolean retryable) {

    public static final String NO_ACCESS_TOKEN = "no_access_token";
    public static final String ACCESS_REJECTED = "access_rejected";
    public static final String TOO_MANY_FILES = "too_many_files";
    public static final String INVALID_RESPONSE = "invalid_response";
    public static final String UNAVAILABLE = "github_unavailable";

    public PullRequestFiles {
        files = files == null ? null : List.copyOf(files);
    }

    public static PullRequestFiles collected(List<ChangedFile> files) {
        return new PullRequestFiles(files, null, false);
    }

    public static PullRequestFiles unavailable(String failure, boolean retryable) {
        return new PullRequestFiles(null, failure, retryable);
    }

    public boolean isCollected() {
        return files != null;
    }
}
