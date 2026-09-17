package com.hoangluongtran0309.releaseflow.change;

/** How well the issue tracker could explain a change that mentions its issues. */
public enum LinkedContextStatus {
    /** The change's own source is an issue tracker, so there is nothing to link to. */
    NOT_SUPPORTED,
    /** The Project has no issue tracker connected. */
    NOT_CONFIGURED,
    /** Everything the change says was read, and it names no issue. */
    NOT_FOUND,
    /** Part of what the change says could not be read, so it may name an issue nobody saw. */
    PARTIAL,
    /** The change names issues the tracker would not show. */
    UNAVAILABLE,
    /** Every issue the change names was read. */
    COLLECTED;

    /** Whether something the change might have explained stayed unknown. */
    public boolean incomplete() {
        return this == PARTIAL || this == UNAVAILABLE;
    }
}
