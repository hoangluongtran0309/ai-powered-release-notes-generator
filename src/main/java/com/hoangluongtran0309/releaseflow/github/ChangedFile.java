package com.hoangluongtran0309.releaseflow.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * One file a pull request touched. {@code previousPath} is set only for a rename.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangedFile(String path, String previousPath, ChangedFileKind kind) {

    public ChangedFile {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
