package com.hoangluongtran0309.releaseflow.source;

/** The kinds of system a Project's changes can come from. */
public enum SourceType {
    GITHUB,
    GITLAB,
    /** An issue tracker: it has no changed files and no history to import. */
    LINEAR;

    /** Whether the provider can be asked for what it recorded before it was connected. */
    public boolean supportsHistoryImport() {
        return this != LINEAR;
    }
}
