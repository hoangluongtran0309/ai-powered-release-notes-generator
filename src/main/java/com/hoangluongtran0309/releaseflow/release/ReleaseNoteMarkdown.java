package com.hoangluongtran0309.releaseflow.release;

import java.util.List;

/**
 * Renders a release note as Markdown. It runs once, at publication, and the result is
 * stored, so later changes to this formatting never alter published text.
 */
final class ReleaseNoteMarkdown {

    private static final String BREAKING_SECTION = "Breaking changes";

    private ReleaseNoteMarkdown() {
    }

    static String render(String version, String summary, List<ReleaseNoteSection> sections) {
        StringBuilder markdown = new StringBuilder("# ").append(escape(version)).append('\n');
        if (summary != null && !summary.isBlank()) {
            markdown.append('\n').append(escape(summary.strip())).append('\n');
        }
        for (ReleaseNoteSection section : sections) {
            markdown.append("\n## ").append(section.title()).append("\n\n");
            for (ReleaseNoteItem item : section.items()) {
                markdown.append("- ")
                        .append(escape(item.title()))
                        .append(" ([#").append(item.pullRequestNumber()).append("](").append(item.url()).append("))");
                if (BREAKING_SECTION.equals(section.title())) {
                    markdown.append(" — ").append(item.category().getLabel());
                }
                markdown.append('\n');
            }
        }
        return markdown.toString();
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if ("\\`*_[]<>".indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
