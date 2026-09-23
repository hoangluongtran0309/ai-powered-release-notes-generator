package com.hoangluongtran0309.releaseflow.jira;

import tools.jackson.databind.JsonNode;

/**
 * Jira writes a description as an Atlassian Document Format tree. The AI and the Change
 * Inbox want prose, so the tree is walked into plain text and capped, because a long
 * description is evidence, not a document to reproduce.
 */
public final class JiraAdfText {

    private JiraAdfText() {
    }

    public static String convert(JsonNode adf, int maxCharacters) {
        StringBuilder text = new StringBuilder();
        append(adf, text, Math.max(0, maxCharacters));
        return text.toString()
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static void append(JsonNode node, StringBuilder text, int max) {
        if (node == null || node.isMissingNode() || text.length() >= max) {
            return;
        }
        if (node.isString()) {
            add(text, node.stringValue(), max);
        }
        String type = node.path("type").asString("");
        if ("text".equals(type)) {
            add(text, node.path("text").asString(""), max);
        }
        JsonNode content = node.path("content");
        if (content.isArray()) {
            content.values().forEach(child -> append(child, text, max));
        }
        // A block ends a line; everything else runs on.
        if (("paragraph".equals(type) || "heading".equals(type) || "listItem".equals(type)) && text.length() < max) {
            add(text, "\n", max);
        }
    }

    private static void add(StringBuilder text, String value, int max) {
        int remaining = max - text.length();
        if (remaining > 0) {
            text.append(value, 0, Math.min(remaining, value.length()));
        }
    }
}
