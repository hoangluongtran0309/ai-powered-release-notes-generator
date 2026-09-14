package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.PullRequestFiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Explainable, fixed rules. A documentation-only file list outranks a title type, which
 * outranks labels; anything the rules do not recognize is Unknown. Unknown, breaking,
 * and triggered changes always need human review.
 */
final class ChangeClassifier {

    private static final Pattern TITLE_TYPE = Pattern.compile(
            "^(feat|fix|perf|docs|refactor|chore|ci|build|test)(\\([^)]*\\))?(!)?:\\s*\\S",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BREAKING_FOOTER = Pattern.compile("(?m)^BREAKING[ -]CHANGE:\\s*\\S");
    private static final Map<String, ChangeCategory> TITLE_TYPES = Map.of(
            "feat", ChangeCategory.FEATURE,
            "fix", ChangeCategory.FIX,
            "perf", ChangeCategory.PERFORMANCE,
            "docs", ChangeCategory.DOCUMENTATION,
            "refactor", ChangeCategory.MAINTENANCE,
            "chore", ChangeCategory.MAINTENANCE,
            "ci", ChangeCategory.MAINTENANCE,
            "build", ChangeCategory.MAINTENANCE,
            "test", ChangeCategory.MAINTENANCE
    );
    private static final Map<String, ChangeCategory> LABELS = Map.ofEntries(
            Map.entry("enhancement", ChangeCategory.FEATURE),
            Map.entry("feature", ChangeCategory.FEATURE),
            Map.entry("bug", ChangeCategory.FIX),
            Map.entry("bugfix", ChangeCategory.FIX),
            Map.entry("performance", ChangeCategory.PERFORMANCE),
            Map.entry("documentation", ChangeCategory.DOCUMENTATION),
            Map.entry("docs", ChangeCategory.DOCUMENTATION),
            Map.entry("dependencies", ChangeCategory.MAINTENANCE),
            Map.entry("maintenance", ChangeCategory.MAINTENANCE),
            Map.entry("chore", ChangeCategory.MAINTENANCE),
            Map.entry("refactor", ChangeCategory.MAINTENANCE)
    );
    private static final Set<String> BREAKING_LABELS = Set.of("breaking-change", "breaking change", "breaking");

    private ChangeClassifier() {
    }

    static ChangeClassification classify(
            MergedPullRequest pullRequest,
            PullRequestFiles files,
            SensitivePathRules sensitivePaths
    ) {
        List<String> reasons = new ArrayList<>();
        List<ReviewTrigger> triggers = new ArrayList<>();
        boolean breaking = false;

        if (files.isCollected()) {
            sensitivePaths.matches(files.files()).stream()
                    .map(ReviewTrigger::sensitivePath)
                    .forEach(triggers::add);
        } else {
            triggers.add(ReviewTrigger.changedFilesUnavailable());
        }
        boolean documentationOnly = files.isCollected()
                && !files.files().isEmpty()
                && files.files().stream().allMatch(file -> isDocumentation(file.path()));
        if (documentationOnly) {
            reasons.add("All changed files are documentation");
        }

        ChangeCategory titleCategory = null;
        Matcher title = TITLE_TYPE.matcher(pullRequest.title().strip());
        if (title.find()) {
            String type = title.group(1).toLowerCase(Locale.ROOT);
            titleCategory = TITLE_TYPES.get(type);
            reasons.add("Title type \"" + type + "\"");
            if (title.group(3) != null) {
                breaking = true;
                reasons.add("Title breaking marker \"!\"");
            }
        }

        Map<String, ChangeCategory> labelCategories = new LinkedHashMap<>();
        for (String label : pullRequest.labels()) {
            String name = label.strip().toLowerCase(Locale.ROOT);
            if (BREAKING_LABELS.contains(name)) {
                breaking = true;
                reasons.add("Breaking label \"" + label + "\"");
            } else if (LABELS.containsKey(name)) {
                labelCategories.put(label, LABELS.get(name));
                reasons.add("Label \"" + label + "\"");
            }
        }

        if (pullRequest.description() != null && BREAKING_FOOTER.matcher(pullRequest.description()).find()) {
            breaking = true;
            reasons.add("BREAKING CHANGE footer");
        }

        ChangeCategory category;
        if (documentationOnly) {
            category = ChangeCategory.DOCUMENTATION;
        } else if (titleCategory != null) {
            category = titleCategory;
        } else if (Set.copyOf(labelCategories.values()).size() == 1) {
            category = labelCategories.values().iterator().next();
        } else {
            category = ChangeCategory.UNKNOWN;
            reasons.add(labelCategories.isEmpty()
                    ? "No category rule matched"
                    : "Conflicting labels " + labelCategories.keySet().stream()
                            .map(label -> "\"" + label + "\"")
                            .collect(Collectors.joining(", ")));
        }

        return new ChangeClassification(
                category,
                breaking,
                breaking || category == ChangeCategory.UNKNOWN || !triggers.isEmpty(),
                reasons,
                triggers
        );
    }

    private static boolean isDocumentation(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("docs/")
                || normalized.endsWith(".md")
                || normalized.endsWith(".adoc")
                || normalized.endsWith(".rst");
    }
}
