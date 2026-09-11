package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeCategory;
import com.hoangluongtran0309.releaseflow.change.ChangeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Groups a release's changes into release-note sections. Breaking changes come first
 * and are listed only there; the other sections follow in a fixed category order.
 */
final class ReleaseNotePreview {

    private static final Pattern CONVENTIONAL_PREFIX = Pattern.compile(
            "^(feat|fix|perf|docs|refactor|chore|ci|build|test)(\\([^)]*\\))?!?:\\s*",
            Pattern.CASE_INSENSITIVE
    );
    private static final Map<ChangeCategory, String> SECTION_TITLES = new LinkedHashMap<>();

    static {
        SECTION_TITLES.put(ChangeCategory.FEATURE, "Features");
        SECTION_TITLES.put(ChangeCategory.FIX, "Fixes");
        SECTION_TITLES.put(ChangeCategory.PERFORMANCE, "Performance");
        SECTION_TITLES.put(ChangeCategory.DOCUMENTATION, "Documentation");
        SECTION_TITLES.put(ChangeCategory.MAINTENANCE, "Maintenance");
    }

    private ReleaseNotePreview() {
    }

    static List<ReleaseNoteSection> sections(List<ChangeView> changes) {
        List<ReleaseNoteItem> items = changes.stream()
                .sorted(Comparator.comparing(ChangeView::mergedAt).thenComparing(ChangeView::pullRequestNumber))
                .map(ReleaseNotePreview::item)
                .toList();

        List<ReleaseNoteSection> sections = new ArrayList<>();
        List<ReleaseNoteItem> breaking = items.stream().filter(ReleaseNoteItem::breaking).toList();
        if (!breaking.isEmpty()) {
            sections.add(new ReleaseNoteSection("Breaking changes", breaking));
        }
        SECTION_TITLES.forEach((category, title) -> {
            List<ReleaseNoteItem> section = items.stream()
                    .filter(item -> !item.breaking() && item.category() == category)
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
                change.breaking()
        );
    }
}
