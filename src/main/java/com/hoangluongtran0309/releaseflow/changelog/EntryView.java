package com.hoangluongtran0309.releaseflow.changelog;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;

import java.time.Instant;
import java.util.UUID;

/** One public entry as a page or a feed needs it. */
public record EntryView(
        UUID id,
        String projectName,
        String releaseVersion,
        String audienceName,
        String language,
        String content,
        Instant publishedAt,
        String url
) {

    /** The note as HTML that is safe to insert into a page or an RSS description. */
    public String html() {
        return MarkdownHtml.render(content);
    }
}
