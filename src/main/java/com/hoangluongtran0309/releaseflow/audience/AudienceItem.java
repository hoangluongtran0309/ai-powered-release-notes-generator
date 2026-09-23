package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;

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
        String pullRequestUrl,
        List<LinkedIssue> linkedIssues
) {

    /** The variables a template may use, in the order the editor offers them. */
    public static final List<String> VARIABLES = List.of(
            "whatChanged",
            "whyChanged",
            "technicalDetail",
            "migrationStep",
            "narrative",
            "pullRequestNumber",
            "pullRequestUrl",
            "linkedIssues"
    );

    static final AudienceItem SAMPLE = new AudienceItem(
            "A safer deployment workflow is now available.",
            "Teams needed clearer control before publishing release notes.",
            "Approval renders Markdown from the stored neutral summary.",
            "No migration is required.",
            "Release managers can review audience-specific notes before they are published.",
            101,
            "https://github.com/acme/app/pull/101",
            List.of(new LinkedIssue("APP-7", "Publish release notes per audience", "", "Story", "Done",
                    "https://acme.atlassian.net/browse/APP-7"))
    );

    public AudienceItem {
        whatChanged = Objects.requireNonNullElse(whatChanged, "");
        whyChanged = Objects.requireNonNullElse(whyChanged, "");
        technicalDetail = Objects.requireNonNullElse(technicalDetail, "");
        migrationStep = Objects.requireNonNullElse(migrationStep, "");
        narrative = Objects.requireNonNullElse(narrative, "");
        pullRequestUrl = Objects.requireNonNullElse(pullRequestUrl, "");
        linkedIssues = linkedIssues == null ? List.of() : List.copyOf(linkedIssues);
    }

    private static Map<String, Object> issue(LinkedIssue issue) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("key", issue.key());
        context.put("title", issue.title());
        context.put("type", issue.type());
        context.put("status", issue.status());
        context.put("url", issue.url());
        return context;
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
        // A section over an empty list simply disappears, so a template may always use it.
        context.put("linkedIssues", linkedIssues.stream().map(AudienceItem::issue).toList());
        return context;
    }
}
