package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates an AI answer against the shared contract. Anything missing or mistyped
 * is rejected as a whole, and so is a category outside the catalog that was sent;
 * unknown extra fields are ignored. Narratives are optional: only the requested
 * audiences are read, and a missing or empty one is left out. A category suggestion
 * is kept only with an Unknown answer and a valid code that is not already listed.
 */
final class AiClassificationParser {

    static final int SUMMARY_FIELD_LIMIT = 2000;
    static final int SUGGESTION_NAME_LIMIT = 120;
    static final int CONTEXT_REASON_LIMIT = 5;
    static final int CONTEXT_REASON_LENGTH = 120;
    static final String INVALID = "The AI returned an invalid classification.";

    private final ObjectMapper objectMapper;

    AiClassificationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiClassification parse(String content, AiClassificationRequest request) {
        final JsonNode result;
        try {
            result = objectMapper.readTree(content == null ? "" : content);
        } catch (JacksonException exception) {
            throw invalid();
        }
        if (result == null || !result.isObject()) {
            throw invalid();
        }
        String code = CategoryRef.normalize(result.path("category").asString("")).orElseThrow(AiClassificationParser::invalid);
        CategoryRef category = request.categories().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
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
        JsonNode context = result.path("context_sufficiency");
        if (!context.isObject() || !context.path("score").isNumber()) {
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
                ),
                narratives(result.path("narratives"), request.audienceCodes()),
                category.isUnknown() ? suggestion(result.path("suggested_category"), request) : null,
                (int) Math.clamp(Math.round(context.path("score").doubleValue()), 0, 100),
                contextReasons(context.path("reasons"))
        );
    }

    private static List<String> contextReasons(JsonNode reasons) {
        List<String> kept = new ArrayList<>();
        if (!reasons.isArray()) {
            return kept;
        }
        for (JsonNode reason : reasons) {
            if (kept.size() == CONTEXT_REASON_LIMIT) {
                break;
            }
            if (reason.isString() && !reason.stringValue().isBlank()) {
                kept.add(cut(reason.stringValue().strip(), CONTEXT_REASON_LENGTH));
            }
        }
        return kept;
    }

    private static CategorySuggestionDraft suggestion(JsonNode node, AiClassificationRequest request) {
        if (!node.isObject() || request.lockedCategory() != null) {
            return null;
        }
        String code = CategoryRef.normalize(node.path("code").asString("")).orElse(null);
        if (code == null || request.categoryCodes().contains(code)) {
            return null;
        }
        String name = node.path("display_name").asString("").strip();
        if (name.isEmpty()) {
            name = code;
        }
        return new CategorySuggestionDraft(
                code,
                cut(name, SUGGESTION_NAME_LIMIT),
                CategoryGroup.fromValue(node.path("group").asString("")).orElse(CategoryGroup.OTHER),
                cut(node.path("rationale").asString("").strip(), CategorySuggestionDraft.RATIONALE_LIMIT)
        );
    }

    private static String cut(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit - 1) + "…";
    }

    private static Map<String, String> narratives(JsonNode narratives, List<String> audienceCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String code : audienceCodes) {
            JsonNode value = narratives.path(code);
            if (value.isString() && !value.stringValue().isBlank()) {
                result.put(code, limit(value.stringValue().strip()));
            }
        }
        return result;
    }

    private static String text(JsonNode core, String field) {
        JsonNode value = core.path(field);
        if (!value.isString()) {
            throw invalid();
        }
        return limit(value.stringValue().strip());
    }

    private static String limit(String value) {
        return value.length() <= SUMMARY_FIELD_LIMIT ? value : value.substring(0, SUMMARY_FIELD_LIMIT - 1) + "…";
    }

    private static AiClassificationException invalid() {
        return new AiClassificationException(INVALID);
    }
}
