package com.hoangluongtran0309.releaseflow.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * An issue a change mentions, as the tracker describes it. It is evidence about the
 * change, never a judgement of it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkedIssue(String key, String title, String description, String type, String status, String url) {

    public LinkedIssue {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(title, "title must not be null");
    }
}
