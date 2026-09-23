package com.hoangluongtran0309.releaseflow.change;

import java.time.Instant;
import java.util.UUID;

/**
 * A possible duplicate as shown to people: the later change, the earlier one it looks
 * like, how similar they are, and what was decided.
 */
public record DuplicateCandidateView(
        UUID id,
        UUID changeId,
        int changePullRequestNumber,
        String changeTitle,
        String changeWhatChanged,
        UUID duplicateOfId,
        int duplicateOfPullRequestNumber,
        String duplicateOfTitle,
        String duplicateOfWhatChanged,
        double similarity,
        DuplicateEvidence evidence,
        DuplicateCandidateStatus status,
        String deciderName,
        Instant createdAt,
        Instant decidedAt
) {

    public boolean open() {
        return status == DuplicateCandidateStatus.OPEN;
    }

    /** The similarity as a whole percentage. */
    public long percent() {
        return Math.round(similarity * 100);
    }
}
