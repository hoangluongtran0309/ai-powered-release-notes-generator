package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;

import java.time.Instant;
import java.util.UUID;

/**
 * One audience's stored note. {@code autoRerender} is true while the note still
 * follows its template, and false once a person has edited it.
 */
public record AudienceNoteView(
        UUID id,
        UUID audienceId,
        String audienceCode,
        String audienceName,
        String language,
        String content,
        boolean autoRerender,
        String lastEditorName,
        Instant updatedAt
) {

    /** The content as HTML that is safe to insert into a page. */
    public String html() {
        return MarkdownHtml.render(content);
    }
}
