package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.github.PullRequestFiles;

import java.util.ArrayList;
import java.util.Comparator;
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
 *
 * <p>Each rule names a group and a preferred category code. The Organization's catalog
 * decides the result: the preferred category if it is active, otherwise the first active
 * category of the group by code. A group without an active category locks nothing.
 */
final class ChangeClassifier {

    private static final Pattern TITLE_TYPE = Pattern.compile(
            "^(feat|fix|perf|docs|refactor|chore|ci|build|test)(\\([^)]*\\))?(!)?:\\s*\\S",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BREAKING_FOOTER = Pattern.compile("(?m)^BREAKING[ -]CHANGE:\\s*\\S");
    private static final Rule FEATURE = new Rule(CategoryGroup.FEATURE, "FEATURE");
    private static final Rule FIX = new Rule(CategoryGroup.FIX, "FIX");
    private static final Rule PERFORMANCE = new Rule(CategoryGroup.PERFORMANCE, "PERFORMANCE");
    private static final Rule DOCUMENTATION = new Rule(CategoryGroup.DOCUMENTATION, "DOCUMENTATION");
    private static final Rule MAINTENANCE = new Rule(CategoryGroup.MAINTENANCE, "MAINTENANCE");
    private static final Map<String, Rule> TITLE_TYPES = Map.of(
            "feat", FEATURE,
            "fix", FIX,
            "perf", PERFORMANCE,
            "docs", DOCUMENTATION,
            "refactor", MAINTENANCE,
            "chore", MAINTENANCE,
            "ci", MAINTENANCE,
            "build", MAINTENANCE,
            "test", MAINTENANCE
    );
    private static final Map<String, Rule> LABELS = Map.ofEntries(
            Map.entry("enhancement", FEATURE),
            Map.entry("feature", FEATURE),
            Map.entry("bug", FIX),
            Map.entry("bugfix", FIX),
            Map.entry("performance", PERFORMANCE),
            Map.entry("documentation", DOCUMENTATION),
            Map.entry("docs", DOCUMENTATION),
            Map.entry("dependencies", MAINTENANCE),
            Map.entry("maintenance", MAINTENANCE),
            Map.entry("chore", MAINTENANCE),
            Map.entry("refactor", MAINTENANCE)
    );
    private static final Set<String> BREAKING_LABELS = Set.of("breaking-change", "breaking change", "breaking");

    private ChangeClassifier() {
    }

    /**
     * @param sensitivePaths the Project's effective sensitive-path rules
     * @param catalog the Organization's active categories
     */
    static ChangeClassification classify(
            MergedPullRequest pullRequest,
            PullRequestFiles files,
            SensitivePaths sensitivePaths,
            List<CategoryRef> catalog
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
        CategoryRef documentation = null;
        boolean documentationOnly = files.isCollected()
                && !files.files().isEmpty()
                && files.files().stream().allMatch(file -> isDocumentation(file.path()));
        if (documentationOnly) {
            reasons.add("All changed files are documentation");
            documentation = resolve(catalog, DOCUMENTATION, reasons);
        }

        CategoryRef titleCategory = null;
        Matcher title = TITLE_TYPE.matcher(pullRequest.title().strip());
        if (title.find()) {
            String type = title.group(1).toLowerCase(Locale.ROOT);
            reasons.add("Title type \"" + type + "\"");
            titleCategory = resolve(catalog, TITLE_TYPES.get(type), reasons);
            if (title.group(3) != null) {
                breaking = true;
                reasons.add("Title breaking marker \"!\"");
            }
        }

        Map<String, CategoryRef> labelCategories = new LinkedHashMap<>();
        for (String label : pullRequest.labels()) {
            String name = label.strip().toLowerCase(Locale.ROOT);
            if (BREAKING_LABELS.contains(name)) {
                breaking = true;
                reasons.add("Breaking label \"" + label + "\"");
            } else if (LABELS.containsKey(name)) {
                reasons.add("Label \"" + label + "\"");
                CategoryRef resolved = resolve(catalog, LABELS.get(name), reasons);
                if (resolved != null) {
                    labelCategories.put(label, resolved);
                }
            }
        }

        if (pullRequest.description() != null && BREAKING_FOOTER.matcher(pullRequest.description()).find()) {
            breaking = true;
            reasons.add("BREAKING CHANGE footer");
        }

        Set<String> labelCodes = labelCategories.values().stream().map(CategoryRef::code).collect(Collectors.toSet());
        CategoryRef category;
        if (documentation != null) {
            category = documentation;
        } else if (titleCategory != null) {
            category = titleCategory;
        } else if (labelCodes.size() == 1) {
            category = labelCategories.values().iterator().next();
        } else {
            category = CategoryRef.UNKNOWN;
            if (labelCodes.size() > 1) {
                reasons.add("Conflicting labels " + labelCategories.keySet().stream()
                        .map(label -> "\"" + label + "\"")
                        .collect(Collectors.joining(", ")));
            } else if (reasons.stream().noneMatch(reason -> reason.startsWith("No active category"))) {
                reasons.add("No category rule matched");
            }
        }

        return new ChangeClassification(
                category,
                breaking,
                breaking || category.isUnknown() || !triggers.isEmpty(),
                reasons,
                triggers
        );
    }

    // The preferred category if active, else the first active one of its group by code.
    private static CategoryRef resolve(List<CategoryRef> catalog, Rule rule, List<String> reasons) {
        CategoryRef resolved = catalog.stream()
                .filter(category -> category.code().equals(rule.preferredCode()))
                .findFirst()
                .or(() -> catalog.stream()
                        .filter(category -> category.group() == rule.group() && !category.isUnknown())
                        .min(Comparator.comparing(CategoryRef::code)))
                .orElse(null);
        if (resolved == null) {
            String reason = "No active category in the " + rule.group().getLabel() + " group";
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }
        return resolved;
    }

    private static boolean isDocumentation(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("docs/")
                || normalized.endsWith(".md")
                || normalized.endsWith(".adoc")
                || normalized.endsWith(".rst");
    }

    private record Rule(CategoryGroup group, String preferredCode) {
    }
}
