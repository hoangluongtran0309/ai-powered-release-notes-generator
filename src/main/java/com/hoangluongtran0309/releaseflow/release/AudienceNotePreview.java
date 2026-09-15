package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;

/** A note rendered from the current changes and templates, before the release is approved. */
public record AudienceNotePreview(String audienceCode, String audienceName, String content) {

    /** The content as HTML that is safe to insert into a page. */
    public String html() {
        return MarkdownHtml.render(content);
    }
}
