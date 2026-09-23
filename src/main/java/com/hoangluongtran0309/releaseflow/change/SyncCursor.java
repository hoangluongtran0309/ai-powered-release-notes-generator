package com.hoangluongtran0309.releaseflow.change;

/**
 * Where a sync stands in a source's list: what the provider calls the page, and how many
 * of that page's items were already handled. The provider half is opaque — a page number
 * for GitHub and GitLab, a page token for Jira — so only its reader interprets it, while
 * the offset belongs to the worker, which is what stops at an item limit.
 *
 * <p>Stored as {@code provider:offset}, split at the last colon so a provider's own
 * cursor may contain one.
 */
record SyncCursor(String provider, int offset) {

    static final SyncCursor START = new SyncCursor("", 0);

    SyncCursor {
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid sync cursor offset " + offset);
        }
        provider = provider == null ? "" : provider;
    }

    static SyncCursor parse(String value) {
        int separator = value.lastIndexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Invalid sync cursor " + value);
        }
        return new SyncCursor(value.substring(0, separator), Integer.parseInt(value.substring(separator + 1)));
    }

    /** The same page, resumed after the items already handled. */
    SyncCursor at(int offset) {
        return new SyncCursor(provider, offset);
    }

    /** Whatever the provider says comes next, from its first item. */
    SyncCursor nextPage(String providerCursor) {
        return new SyncCursor(providerCursor == null ? "" : providerCursor, 0);
    }

    @Override
    public String toString() {
        return provider + ":" + offset;
    }
}
