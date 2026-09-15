package com.hoangluongtran0309.releaseflow.audience;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The values one audience template sees for one change. Text values are never null;
 * an empty value counts as false in a section, so {@code {{#narrative}}...{{/narrative}}}
 * disappears when there is no narrative.
 */
public record AudienceItem(
        String whatChanged,
        String whyChanged,
        String technicalDetail,
        String migrationStep,
        String narrative,
        int pullRequestNumber,
        String pullRequestUrl
) {

    /** The variables a template may use, in the order the editor offers them. */
    public static final List<String> VARIABLES = List.of(
            "whatChanged",
            "whyChanged",
            "technicalDetail",
            "migrationStep",
            "narrative",
            "pullRequestNumber",
            "pullRequestUrl"
    );

    static final AudienceItem SAMPLE = new AudienceItem(
            "A safer deployment workflow is now available.",
            "Teams needed clearer control before publishing release notes.",
            "Approval renders Markdown from the stored neutral summary.",
            "No migration is required.",
            "Release managers can review audience-specific notes before they are published.",
            101,
            "https://github.com/acme/app/pull/101"
    );

    public AudienceItem {
        whatChanged = Objects.requireNonNullElse(whatChanged, "");
        whyChanged = Objects.requireNonNullElse(whyChanged, "");
        technicalDetail = Objects.requireNonNullElse(technicalDetail, "");
        migrationStep = Objects.requireNonNullElse(migrationStep, "");
        narrative = Objects.requireNonNullElse(narrative, "");
        pullRequestUrl = Objects.requireNonNullElse(pullRequestUrl, "");
    }

    Map<String, Object> context() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("whatChanged", whatChanged);
        context.put("whyChanged", whyChanged);
        context.put("technicalDetail", technicalDetail);
        context.put("migrationStep", migrationStep);
        context.put("narrative", narrative);
        context.put("pullRequestNumber", pullRequestNumber);
        context.put("pullRequestUrl", pullRequestUrl);
        return context;
    }
}
