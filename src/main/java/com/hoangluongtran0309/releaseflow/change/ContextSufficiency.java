package com.hoangluongtran0309.releaseflow.change;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Combines the AI's context score with fixed caps for pull requests that plainly say
 * too little. The caps can only lower the score, never raise it.
 */
final class ContextSufficiency {

    static final String DESCRIPTION_MISSING = "DESCRIPTION_MISSING";
    static final String TITLE_TOO_SHORT = "TITLE_TOO_SHORT";
    static final String GENERIC_TITLE = "GENERIC_TITLE";

    private static final Set<String> GENERIC_TITLES = Set.of("fix", "update", "change", "cleanup", "misc", "wip");
    private static final int SHORT_TITLE = 12;
    private static final int SHORT_DESCRIPTION = 160;

    private ContextSufficiency() {
    }

    static ContextAssessment assess(MergedPullRequest pullRequest, int aiScore, List<String> aiReasons, int threshold) {
        int score = Math.clamp(aiScore, 0, 100);
        Set<String> reasons = new LinkedHashSet<>(aiReasons);
        String title = pullRequest.title() == null ? "" : pullRequest.title().strip();
        String description = pullRequest.description() == null ? "" : pullRequest.description().strip();
        if (description.isEmpty()) {
            score = Math.min(score, 30);
            reasons.add(DESCRIPTION_MISSING);
        }
        if (title.length() < SHORT_TITLE) {
            score = Math.min(score, 50);
            reasons.add(TITLE_TOO_SHORT);
        }
        if (GENERIC_TITLES.contains(title.toLowerCase(Locale.ROOT)) && description.length() < SHORT_DESCRIPTION) {
            score = Math.min(score, 40);
            reasons.add(GENERIC_TITLE);
        }
        return new ContextAssessment(
                score,
                score < threshold ? ContextStatus.INSUFFICIENT : ContextStatus.SUFFICIENT,
                List.copyOf(reasons)
        );
    }
}
