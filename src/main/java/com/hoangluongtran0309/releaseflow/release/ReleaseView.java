package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A release as shown to users. For a published release, {@code preview} and
 * {@code markdown} come from the immutable snapshot rather than from live changes.
 */
public record ReleaseView(
        UUID id,
        UUID projectId,
        String version,
        String summary,
        ReleaseStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<ChangeView> changes,
        List<ReleaseNoteSection> preview,
        Instant publishedAt,
        String publisherName,
        String markdown
) {

    public ReleaseView {
        changes = List.copyOf(changes);
        preview = List.copyOf(preview);
    }
}
