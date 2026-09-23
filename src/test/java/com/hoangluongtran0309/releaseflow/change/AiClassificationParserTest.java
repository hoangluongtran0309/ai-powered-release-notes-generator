package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategorySuggestionDraft;
import com.hoangluongtran0309.releaseflow.support.TestCategories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClassificationParserTest {

    private static final AiClassificationRequest REQUEST = OpenAiCompatibleClassifierTest.request(null, null, "en");
    private static final String SUMMARY = "\"context_sufficiency\":{\"score\":90,\"reasons\":[]},\"neutral_core\":{\"what_changed\":\"Adds audit logs.\",\"why_changed\":\"\","
            + "\"technical_detail\":\"\",\"migration_step\":\"\"}";

    private final AiClassificationParser parser = new AiClassificationParser(new ObjectMapper());

    @Test
    void parsesAValidAnswerAndIgnoresExtraFields() {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":true,"needs_human_review":false,"confidence":0.4,
                 "context_sufficiency":{"score":90,"reasons":[]},"neutral_core":{"what_changed":"  Trims input.  ","why_changed":"","technical_detail":"Strip()",
                 "migration_step":"","narratives":{"end_user":"ignored"}}}
                """, REQUEST);

        assertThat(answer).isEqualTo(new AiClassification(
                TestCategories.FIX,
                true,
                false,
                new NeutralSummary("Trims input.", "", "Strip()", ""),
                Map.of(),
                null,
                90,
                List.of()
        ));
    }

    @Test
    void keepsAProposedCategoryWithAnUnknownAnswer() {
        AiClassification answer = parser.parse("""
                {"category":"UNKNOWN","breaking_change":false,"needs_human_review":true,%s,
                 "suggested_category":{"code":" audit-log ","display_name":"Audit log","group":"feature",
                 "rationale":"No listed category covers auditing."}}
                """.formatted(SUMMARY), REQUEST);

        assertThat(answer.category()).isEqualTo(TestCategories.UNKNOWN);
        assertThat(answer.suggestion()).isEqualTo(new CategorySuggestionDraft(
                "AUDIT_LOG", "Audit log", CategoryGroup.FEATURE, "No listed category covers auditing."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"category\":\"feature\",\"suggested_category\":{\"code\":\"AUDIT\",\"display_name\":\"Audit\",\"group\":\"feature\",\"rationale\":\"\"}",
            "\"category\":\"unknown\",\"suggested_category\":{\"code\":\"\",\"display_name\":\"\",\"group\":\"other\",\"rationale\":\"\"}",
            "\"category\":\"unknown\",\"suggested_category\":{\"code\":\"9lives\",\"display_name\":\"Cats\",\"group\":\"other\",\"rationale\":\"\"}",
            "\"category\":\"unknown\",\"suggested_category\":{\"code\":\"fix\",\"display_name\":\"Fix\",\"group\":\"fix\",\"rationale\":\"\"}",
            "\"category\":\"unknown\",\"suggested_category\":null",
            "\"category\":\"unknown\""
    })
    void dropsProposalsThatAreEmptyInvalidAlreadyListedOrNotForAnUnknownAnswer(String fields) {
        AiClassification answer = parser.parse(
                "{" + fields + ",\"breaking_change\":false,\"needs_human_review\":true," + SUMMARY + "}", REQUEST);

        assertThat(answer.suggestion()).isNull();
    }

    @Test
    void defaultsAProposalsNameAndGroup() {
        AiClassification answer = parser.parse("""
                {"category":"unknown","breaking_change":false,"needs_human_review":true,%s,
                 "suggested_category":{"code":"COMPLIANCE","display_name":"  ","group":"legal","rationale":"%s"}}
                """.formatted(SUMMARY, "r".repeat(1500)), REQUEST);

        assertThat(answer.suggestion().displayName()).isEqualTo("COMPLIANCE");
        assertThat(answer.suggestion().group()).isEqualTo(CategoryGroup.OTHER);
        assertThat(answer.suggestion().rationale()).hasSize(CategorySuggestionDraft.RATIONALE_LIMIT).endsWith("…");
    }

    @Test
    void keepsTheNarrativesOfRequestedAudiencesOnly() {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "context_sufficiency":{"score":90,"reasons":[]},"neutral_core":{"what_changed":"Trims input.","why_changed":"","technical_detail":"","migration_step":""},
                 "narratives":{"operator":"  Watch the import logs.  ","end_user":"   ","contributor":"Not requested."}}
                """, REQUEST);

        assertThat(answer.narratives()).containsExactly(Map.entry("operator", "Watch the import logs."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"narratives\":null", ",\"narratives\":[]", ",\"narratives\":{\"operator\":42}"})
    void acceptsMissingOrMistypedNarrativesWithoutThem(String narratives) {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "context_sufficiency":{"score":90,"reasons":[]},"neutral_core":{"what_changed":"Trims input.","why_changed":"","technical_detail":"","migration_step":""}%s}
                """.formatted(narratives), REQUEST);

        assertThat(answer.narratives()).isEmpty();
        assertThat(answer.summary().whatChanged()).isEqualTo("Trims input.");
    }

    @ParameterizedTest
    @CsvSource({"150, 100", "-3, 0", "59.6, 60"})
    void clampsAndRoundsTheContextScore(String score, int expected) {
        AiClassification answer = parser.parse("""
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "context_sufficiency":{"score":%s,"reasons":[" NO_PROBLEM_STATEMENT ","",42,"A","B","C","D","E"]},
                 "neutral_core":{"what_changed":"Trims input.","why_changed":"","technical_detail":"","migration_step":""}}
                """.formatted(score), REQUEST);

        assertThat(answer.contextScore()).isEqualTo(expected);
        assertThat(answer.contextReasons()).containsExactly("NO_PROBLEM_STATEMENT", "A", "B", "C", "D");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"context_sufficiency\":null", ",\"context_sufficiency\":{\"reasons\":[]}",
            ",\"context_sufficiency\":{\"score\":\"high\",\"reasons\":[]}"})
    void rejectsAnAnswerWithoutAContextScore(String context) {
        assertThatThrownBy(() -> parser.parse("""
                {"category":"fix","breaking_change":false,"needs_human_review":false%s,
                 "neutral_core":{"what_changed":"Trims input.","why_changed":"","technical_detail":"","migration_step":""}}
                """.formatted(context), REQUEST))
                .isInstanceOf(AiClassificationException.class);
    }

    @Test
    void shortensOverlongSummaryFields() {
        String answer = """
                {"category":"fix","breaking_change":false,"needs_human_review":false,
                 "context_sufficiency":{"score":90,"reasons":[]},"neutral_core":{"what_changed":"%s","why_changed":"","technical_detail":"","migration_step":""}}
                """.formatted("w".repeat(3000));

        assertThat(parser.parse(answer, REQUEST).summary().whatChanged())
                .hasSize(AiClassificationParser.SUMMARY_FIELD_LIMIT)
                .endsWith("…");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "not json",
            "[]",
            "{\"category\":\"security\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"performance-ish\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":\"no\",\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":\"\",\"technical_detail\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"  \",\"why_changed\":\"\",\"technical_detail\":\"\",\"migration_step\":\"\"}}",
            "{\"category\":\"fix\",\"breaking_change\":false,\"needs_human_review\":false,\"neutral_core\":{\"what_changed\":\"x\",\"why_changed\":null,\"technical_detail\":\"\",\"migration_step\":\"\"}}"
    })
    void rejectsIncompleteOrMistypedAnswers(String content) {
        assertThatThrownBy(() -> parser.parse(content, REQUEST))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(AiClassificationParser.INVALID);
    }
}
