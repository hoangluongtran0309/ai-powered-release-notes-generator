package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A release as shown to users. For a published release, {@code preview} and
 * {@code markdown} come from the immutable snapshot rather than from live changes.
 * {@code decisions} holds the review decision on each change that has one.
 */
public record ReleaseView(
        UUID id,
        UUID projectId,
        String version,
        String summary,
        ReleaseStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant plannedReleaseAt,
        List<ChangeView> changes,
        List<ReleaseDecisionView> decisions,
        int reviewedCount,
        List<ReleaseNoteSection> preview,
        Instant approvedAt,
        String approverName,
        Instant publishedAt,
        String publisherName,
        String markdown
) {

    public ReleaseView {
        changes = List.copyOf(changes);
        decisions = List.copyOf(decisions);
        preview = List.copyOf(preview);
    }

    /** The decision on a change of this release, or {@code null} if it has none yet. */
    public ReleaseDecisionView decision(UUID changeId) {
        return decisions.stream().filter(decision -> decision.changeId().equals(changeId)).findFirst().orElse(null);
    }

    public boolean breaking() {
        return changes.stream().anyMatch(ChangeView::breaking);
    }

    public boolean fullyReviewed() {
        return !changes.isEmpty() && reviewedCount == changes.size();
    }
}
