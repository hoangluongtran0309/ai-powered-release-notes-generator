package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.AudienceItem;
import com.hoangluongtran0309.releaseflow.audience.AudienceTemplate;
import com.hoangluongtran0309.releaseflow.change.ChangeView;
import com.hoangluongtran0309.releaseflow.change.NeutralSummary;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Renders the release note one audience reads: a title, a "What's New" overview with
 * counts, a breaking-change warning, and one section per kind of change, each item
 * written by the audience's template. It is a pure function of its input and never
 * calls AI. Labels follow the note's language (English or Vietnamese, otherwise
 * English).
 */
final class ReleaseNoteDigest {

    enum Section {
        BREAKING, FEATURES, FIXES, PERFORMANCE, DOCUMENTATION, MAINTENANCE, OTHER;

        static Section of(ChangeView change) {
            if (change.breaking()) {
                return BREAKING;
            }
            return switch (change.categoryGroup()) {
                case FEATURE -> FEATURES;
                case FIX -> FIXES;
                case PERFORMANCE -> PERFORMANCE;
                case DOCUMENTATION -> DOCUMENTATION;
                case MAINTENANCE -> MAINTENANCE;
                case OTHER -> OTHER;
            };
        }
    }

    private static final String BUNDLE = "messages/release-note";
    private static final String ITEM_SEPARATOR = "\n\n";
    private static final int MAX_HEADING = 6;
    private static final int HEADING_DEMOTION = 2;

    private ReleaseNoteDigest() {
    }

    static String render(
            String version,
            String summary,
            List<ChangeView> changes,
            String templateBody,
            String audienceCode,
            String languageTag
    ) {
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("A release note needs at least one change.");
        }
        Labels labels = new Labels(languageTag);
        Map<Section, List<ChangeView>> sections = sections(changes);

        StringBuilder note = new StringBuilder("# ")
                .append(labels.format("title", ReleaseNoteMarkdown.escape(version)))
                .append("\n\n");
        if (summary != null && !summary.isBlank()) {
            note.append(ReleaseNoteMarkdown.escape(summary.strip())).append("\n\n");
        }
        note.append("## ").append(labels.raw("whatsNew")).append("\n\n").append(overview(sections, labels)).append('\n');
        sections.forEach((section, items) -> {
            note.append("\n## ").append(labels.raw("section." + section.name())).append("\n\n");
            note.append(String.join(ITEM_SEPARATOR, items.stream()
                    .map(change -> demoteHeadings(AudienceTemplate.render(templateBody, item(change, audienceCode)).strip()))
                    .toList()));
            note.append('\n');
        });
        return note.toString().strip() + "\n";
    }

    // Breaking changes first, then the other kinds in a fixed order; empty sections are left out.
    static Map<Section, List<ChangeView>> sections(List<ChangeView> changes) {
        Map<Section, List<ChangeView>> sections = new EnumMap<>(Section.class);
        changes.stream()
                .sorted(Comparator.comparing(ChangeView::mergedAt).thenComparing(ChangeView::pullRequestNumber))
                .forEach(change -> sections.computeIfAbsent(Section.of(change), ignored -> new ArrayList<>()).add(change));
        return sections;
    }

    /**
     * What one change says to one audience. Without a summary, what changed is the pull
     * request title, escaped because it is untrusted text.
     */
    static AudienceItem item(ChangeView change, String audienceCode) {
        NeutralSummary summary = change.neutralSummary();
        if (summary == null) {
            return new AudienceItem(
                    ReleaseNoteMarkdown.escape(ReleaseNotePreview.displayTitle(change.title())),
                    "",
                    "",
                    "",
                    change.narrative(audienceCode),
                    change.pullRequestNumber(),
                    change.url()
            );
        }
        return new AudienceItem(
                summary.whatChanged(),
                summary.whyChanged(),
                summary.technicalDetail(),
                summary.migrationStep(),
                change.narrative(audienceCode),
                change.pullRequestNumber(),
                change.url()
        );
    }

    private static String overview(Map<Section, List<ChangeView>> sections, Labels labels) {
        int total = sections.values().stream().mapToInt(List::size).sum();
        List<String> counts = new ArrayList<>();
        sections.forEach((section, items) -> counts.add(labels.format("count." + section.name(), items.size())));
        StringBuilder overview = new StringBuilder(labels.format("overview", total, labels.join(counts)));
        List<ChangeView> breaking = sections.get(Section.BREAKING);
        if (breaking != null) {
            overview.append("\n\n> ").append(labels.format("breakingWarning", breaking.size()));
        }
        return overview.toString();
    }

    /**
     * Moves every Markdown heading of an item down two levels, at most to level six, so
     * an item never competes with the note's own headings. Fenced code is left alone.
     */
    static String demoteHeadings(String body) {
        StringBuilder demoted = new StringBuilder();
        boolean insideFence = false;
        String[] lines = body.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                insideFence = !insideFence;
            } else if (!insideFence) {
                int hashes = 0;
                while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') {
                    hashes++;
                }
                boolean heading = hashes >= 1 && hashes <= MAX_HEADING
                        && (hashes == trimmed.length() || trimmed.charAt(hashes) == ' ');
                if (heading) {
                    line = "#".repeat(Math.min(hashes + HEADING_DEMOTION, MAX_HEADING)) + trimmed.substring(hashes);
                }
            }
            demoted.append(line);
            if (index < lines.length - 1) {
                demoted.append('\n');
            }
        }
        return demoted.toString();
    }

    private static final class Labels {

        private final ResourceBundle bundle;
        private final Locale locale;

        Labels(String languageTag) {
            this.locale = Locale.forLanguageTag(languageTag);
            this.bundle = ResourceBundle.getBundle(
                    BUNDLE,
                    locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES)
            );
        }

        String raw(String key) {
            return bundle.getString(key);
        }

        String format(String key, Object... arguments) {
            return new MessageFormat(raw(key), locale).format(arguments);
        }

        String join(List<String> parts) {
            if (parts.size() == 1) {
                return parts.getFirst();
            }
            if (parts.size() == 2) {
                return format("list.pair", parts.get(0), parts.get(1));
            }
            String joined = parts.getFirst();
            for (String part : parts.subList(1, parts.size() - 1)) {
                joined = format("list.middle", joined, part);
            }
            return format("list.last", joined, parts.getLast());
        }
    }
}
