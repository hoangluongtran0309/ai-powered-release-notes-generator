package com.hoangluongtran0309.releaseflow.change;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates an AI answer against the shared contract. Anything missing or mistyped
 * is rejected as a whole; unknown extra fields are ignored.
 */
final class AiClassificationParser {

    static final int SUMMARY_FIELD_LIMIT = 2000;
    static final String INVALID = "The AI returned an invalid classification.";

    private final ObjectMapper objectMapper;

    AiClassificationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiClassification parse(String content) {
        final JsonNode result;
        try {
            result = objectMapper.readTree(content == null ? "" : content);
        } catch (JacksonException exception) {
            throw invalid();
        }
        if (result == null || !result.isObject()) {
            throw invalid();
        }
        ChangeCategory category = ChangeCategory.fromValue(result.path("category").asString(""))
                .orElseThrow(AiClassificationParser::invalid);
        JsonNode breaking = result.path("breaking_change");
        JsonNode needsReview = result.path("needs_human_review");
        if (!breaking.isBoolean() || !needsReview.isBoolean()) {
            throw invalid();
        }
        JsonNode core = result.path("neutral_core");
        if (!core.isObject()) {
            throw invalid();
        }
        String whatChanged = text(core, "what_changed");
        if (whatChanged.isBlank()) {
            throw invalid();
        }
        return new AiClassification(
                category,
                breaking.booleanValue(),
                needsReview.booleanValue(),
                new NeutralSummary(
                        whatChanged,
                        text(core, "why_changed"),
                        text(core, "technical_detail"),
                        text(core, "migration_step")
                )
        );
    }

    private static String text(JsonNode core, String field) {
        JsonNode value = core.path(field);
        if (!value.isString()) {
            throw invalid();
        }
        String stripped = value.stringValue().strip();
        return stripped.length() <= SUMMARY_FIELD_LIMIT
                ? stripped
                : stripped.substring(0, SUMMARY_FIELD_LIMIT - 1) + "…";
    }

    private static AiClassificationException invalid() {
        return new AiClassificationException(INVALID);
    }
}
