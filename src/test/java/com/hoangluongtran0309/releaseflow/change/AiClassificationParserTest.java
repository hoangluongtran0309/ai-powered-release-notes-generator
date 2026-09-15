package com.hoangluongtran0309.releaseflow.change;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClassificationParserTest {

    private static final List<String> AUDIENCES = List.of("operator", "end_user");

    private final AiClassificationParser parser = new AiClassificationParser(new ObjectMapper());

    @Test
    void parsesAValidAnswerAndIgnoresExtraFields() {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":true,"needs_human_review":false,"confidence":0.4,
                 "neutral_core":{"what_changed":"  Trims input.  ","why_changed":"","technical_detail":"Strip()",
                 "migration_step":"","narratives":{"end_user":"ignored"}}}
                """, AUDIENCES);

        assertThat(answer).isEqualTo(new AiClassification(
                ChangeCategory.FIX,
                true,
                false,
                new NeutralSummary("Trims input.", "", "Strip()", ""),
                Map.of()
        ));
    }

    @Test
    void keepsTheNarrativesOfRequestedAudiencesOnly() {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "neutral_core":{"what_changed":"Trims input.","why_changed":"","technical_detail":"","migration_step":""},
                 "narratives":{"operator":"  Watch the import logs.  ","end_user":"   ","contributor":"Not requested."}}
                """, AUDIENCES);

        assertThat(answer.narratives()).containsExactly(Map.entry("operator", "Watch the import logs."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"narratives\":null", ",\"narratives\":[]", ",\"narratives\":{\"operator\":42}"})
    void acceptsMissingOrMistypedNarrativesWithoutThem(String narratives) {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "neutral_core":{"what_changed":"Trims input.","why_changed":"","technical_detail":"","migration_step":""}%s}
                """.formatted(narratives), AUDIENCES);

        assertThat(answer.narratives()).isEmpty();
        assertThat(answer.summary().whatChanged()).isEqualTo("Trims input.");
    }

    @Test
    void shortensOverlongSummaryFields() {
        String answer = """
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "neutral_core":{"what_changed":"%s","why_changed":"","technical_detail":"","migration_step":""}}
                """.formatted("w".repeat(3000));

        assertThat(parser.parse(answer, AUDIENCES).summary().whatChanged())
                .hasSize(AiClassificationParser.SUMMARY_FIELD_LIMIT)
                .endsWith("…");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[]",
            "{\"category\":\"security\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":\"no\",\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"  \",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":null,\"technical_detail\":\"\",\"migration_step\":\"\"}}"
    })
    void rejectsIncompleteOrMistypedAnswers(String content) {
        assertThatThrownBy(() -> parser.parse(content, AUDIENCES))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(AiClassificationParser.INVALID);
    }
}
