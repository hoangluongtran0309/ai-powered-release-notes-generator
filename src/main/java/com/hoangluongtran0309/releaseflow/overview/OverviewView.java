package com.hoangluongtran0309.releaseflow.overview;

import java.util.List;
import java.util.UUID;

/**
 * Everything the workspace overview shows: the Project it is about, the Projects one can
 * switch to, four counts, and the single next step.
 *
 * @param projectId null when the Organization has no Project at all
 * @param step the first unfinished thing, which decides the card and its link
 */
public record OverviewView(
        UUID projectId,
        String projectName,
        List<ProjectOption> projects,
        long changes,
        long needsReview,
        long breaking,
        long releases,
        boolean sourceConnected,
        NextStep step
) {

    public record ProjectOption(UUID id, String name, boolean selected) {
    }

    /** Which of the four steps of the strip this workspace has reached. */
    public int stage() {
        if (projectId == null) {
            return 0;
        }
        if (!sourceConnected) {
            return 1;
        }
        if (changes == 0) {
            return 2;
        }
        return releases == 0 ? 3 : 4;
    }
}
