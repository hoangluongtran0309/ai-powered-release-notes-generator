package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.change.ChangeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Groups a release's changes into release-note sections. Breaking changes come first
 * and are listed only there; the other sections follow in a fixed group order. Unknown
 * changes are left out; review gives them a category before approval.
 */
final class ReleaseNotePreview {

    private static final Pattern CONVENTIONAL_PREFIX = Pattern.compile(
            "^(feat|fix|perf|docs|refactor|chore|ci|build|test)(\\([^)]*\\))?!?:\\s*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<CategoryGroup, String> SECTION_TITLES = new LinkedHashMap<>();

    static {
        SECTION_TITLES.put(CategoryGroup.FEATURE, "Features");
        SECTION_TITLES.put(CategoryGroup.FIX, "Fixes");
        SECTION_TITLES.put(CategoryGroup.PERFORMANCE, "Performance");
        SECTION_TITLES.put(CategoryGroup.DOCUMENTATION, "Documentation");
        SECTION_TITLES.put(CategoryGroup.MAINTENANCE, "Maintenance");
        SECTION_TITLES.put(CategoryGroup.OTHER, "Other changes");
    }

    private ReleaseNotePreview() {
    }

    static List<ReleaseNoteSection> sections(List<ChangeView> changes) {
        List<ChangeView> sorted = changes.stream()
                .filter(change -> change.breaking() || !change.unknownCategory())
                .sorted(Comparator.comparing(ChangeView::mergedAt).thenComparing(ChangeView::pullRequestNumber))
                .toList();

        List<ReleaseNoteSection> sections = new ArrayList<>();
        List<ReleaseNoteItem> breaking = sorted.stream().filter(ChangeView::breaking).map(ReleaseNotePreview::item).toList();
        if (!breaking.isEmpty()) {
            sections.add(new ReleaseNoteSection("Breaking changes", breaking));
        }
        SECTION_TITLES.forEach((group, title) -> {
            List<ReleaseNoteItem> section = sorted.stream()
                    .filter(change -> !change.breaking() && change.categoryGroup() == group)
                    .map(ReleaseNotePreview::item)
                    .toList();
            if (!section.isEmpty()) {
                sections.add(new ReleaseNoteSection(title, section));
            }
        });
        return List.copyOf(sections);
    }

    static String displayTitle(String title) {
        String stripped = CONVENTIONAL_PREFIX.matcher(title.strip()).replaceFirst("");
        return stripped.isBlank() ? title.strip() : stripped;
    }

    private static ReleaseNoteItem item(ChangeView change) {
        return new ReleaseNoteItem(
                change.id(),
                change.pullRequestNumber(),
                displayTitle(change.title()),
                change.url(),
                change.category(),
                change.breaking(),
                change.categoryName()
        );
    }
}
