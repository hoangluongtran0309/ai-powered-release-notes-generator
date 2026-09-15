package com.hoangluongtran0309.releaseflow.change;

/** How a change reached ReleaseFlow: a webhook delivery, or an import of the source's history. */
public enum ChangeOrigin {
    WEBHOOK,
    IMPORT
}
