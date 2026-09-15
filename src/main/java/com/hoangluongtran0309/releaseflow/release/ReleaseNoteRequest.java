package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A person's text for one audience's note. */
public class ReleaseNoteRequest {

    static final int MAX_LENGTH = 200_000;

    @NotBlank(message = "A release note cannot be empty.")
    @Size(max = MAX_LENGTH, message = "A release note must not exceed 200000 characters.")
    private String content;

    public String getContent() {
        return content;
    }

    // Browsers submit textarea line breaks as CRLF; notes are stored with LF.
    public void setContent(String content) {
        this.content = content == null ? null : content.replace("\r\n", "\n").strip() + "\n";
    }
}
