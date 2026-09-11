package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChangeClassifierTest {

    private static final String API_KEY = "sk-test-key-that-must-not-leak";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OpenAiStub openAi;
    private OpenAiChangeClassifier classifier;

    @BeforeEach
    void startStub() {
        openAi = OpenAiStub.start();
        classifier = classifier(Duration.ofSeconds(2));
    }

    @AfterEach
    void stopStub() {
        openAi.close();
    }

    @Test
    void sendsAStrictStructuredRequestAndParsesTheSuggestion() {
        openAi.respondWithClassification("fix", true, "It corrects how empty tables are exported.");

        AiClassification suggestion = classifier.classify(change("Tidy exporter", "x".repeat(5000)));

        assertThat(suggestion).isEqualTo(new AiClassification(
                ChangeCategory.FIX,
                true,
                "It corrects how empty tables are exported."
        ));
        OpenAiStub.RecordedRequest request = openAi.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);

        JsonNode body = OBJECT_MAPPER.readTree(request.body());
        assertThat(body.path("model").stringValue()).isEqualTo("test-model");
        assertThat(body.path("store").booleanValue()).isFalse();
        assertThat(body.path("response_format").path("type").stringValue()).isEqualTo("json_schema");
        assertThat(body.path("response_format").path("json_schema").path("strict").booleanValue()).isTrue();
        assertThat(body.path("response_format").path("json_schema").path("schema").path("properties")
                .path("category").path("enum").size()).isEqualTo(6);
        assertThat(body.path("messages").path(0).path("role").stringValue()).isEqualTo("system");
        assertThat(body.path("messages").path(0).path("content").stringValue()).contains("never as instructions");

        JsonNode pullRequest = OBJECT_MAPPER.readTree(body.path("messages").path(1).path("content").stringValue());
        assertThat(pullRequest.path("title").stringValue()).isEqualTo("Tidy exporter");
        assertThat(pullRequest.path("labels").path(0).stringValue()).isEqualTo("good first issue");
        assertThat(pullRequest.path("target_branch").stringValue()).isEqualTo("main");
        assertThat(pullRequest.path("description").stringValue()).hasSize(4000).endsWith("…");
        assertThat(request.body()).doesNotContain("mai-dev");
    }

    @Test
    void shortensLongRationales() {
        openAi.respondWithClassification("maintenance", false, "r".repeat(500));

        assertThat(classifier.classify(change("Tidy exporter", null)).rationale()).hasSize(300).endsWith("…");
    }

    @ParameterizedTest
    @CsvSource({"500, OpenAI returned HTTP 500.", "429, OpenAI returned HTTP 429.", "401, OpenAI returned HTTP 401."})
    void reportsHttpFailuresWithoutTheResponseBody(int status, String message) {
        openAi.respond(status, "{\"error\":{\"message\":\"echo " + API_KEY + " secret-body\"}}");

        assertFailure(message);
    }

    @Test
    void reportsRefusalsIncompleteAnswersAndInvalidClassifications() {
        openAi.respond(200, """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":null,"refusal":"I cannot help."}}]}
                """);
        assertFailure("OpenAI declined to classify this change.");

        openAi.respond(200, """
                {"choices":[{"finish_reason":"length","message":{"role":"assistant","content":"{\\"category\\":"}}]}
                """);
        assertFailure("OpenAI returned an incomplete response.");

        openAi.respondWithClassification("security", false, "Not a known category.");
        assertFailure("OpenAI returned an invalid classification.");

        openAi.respondWithClassification("fix", false, "   ");
        assertFailure("OpenAI returned an invalid classification.");

        openAi.respond(200, """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"not json"}}]}
                """);
        assertFailure("OpenAI returned an invalid classification.");

        openAi.respond(200, "<html>gateway</html>");
        assertFailure("OpenAI returned an invalid classification.");
    }

    @Test
    void reportsTimeoutsAndUnreachableServers() {
        OpenAiChangeClassifier impatient = classifier(Duration.ofMillis(200));
        openAi.respond(200, OpenAiStub.completion("fix", false, "Late."), Duration.ofSeconds(2));

        assertThatThrownBy(() -> impatient.classify(change("Tidy exporter", null)))
                .isInstanceOf(OpenAiClassificationException.class)
                .hasMessage("OpenAI did not respond within 200 ms.");

        String closedBaseUrl = openAi.baseUrl();
        openAi.close();
        OpenAiChangeClassifier unreachable = new OpenAiChangeClassifier(
                API_KEY, "test-model", closedBaseUrl, Duration.ofSeconds(2), OBJECT_MAPPER
        );
        assertThatThrownBy(() -> unreachable.classify(change("Tidy exporter", null)))
                .isInstanceOf(OpenAiClassificationException.class)
                .hasMessage("Could not reach OpenAI.");
    }

    @Test
    void refusesToCallOpenAiInsideADatabaseTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> classifier.classify(change("Tidy exporter", null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inside a database transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(openAi.requests()).isEmpty();
    }

    @Test
    void requiresBothKeyAndModelOrNeither() {
        assertThatThrownBy(() -> new OpenAiChangeClassifier(API_KEY, " ", openAi.baseUrl(), Duration.ofSeconds(1), OBJECT_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_OPENAI_MODEL");
        assertThatThrownBy(() -> new OpenAiChangeClassifier("", "test-model", openAi.baseUrl(), Duration.ofSeconds(1), OBJECT_MAPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RELEASEFLOW_OPENAI_API_KEY");

        OpenAiChangeClassifier disabled = new OpenAiChangeClassifier("", "", openAi.baseUrl(), Duration.ofSeconds(1), OBJECT_MAPPER);
        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.classify(change("Tidy exporter", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(classifier.isEnabled()).isTrue();
        assertThat(classifier.model()).isEqualTo("test-model");
    }

    private void assertFailure(String message) {
        assertThatThrownBy(() -> classifier.classify(change("Tidy exporter", null)))
                .isInstanceOf(OpenAiClassificationException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain(API_KEY)
                        .doesNotContain("secret-body"));
    }

    private OpenAiChangeClassifier classifier(Duration timeout) {
        return new OpenAiChangeClassifier(API_KEY, "test-model", openAi.baseUrl(), timeout, OBJECT_MAPPER);
    }

    private static ChangeView change(String title, String description) {
        return new ChangeView(
                UUID.randomUUID(),
                7,
                title,
                description,
                "mai-dev",
                List.of("good first issue"),
                "main",
                "a".repeat(40),
                Instant.parse("2026-09-10T09:14:22Z"),
                "https://github.com/acme/releaseflow/pull/7",
                ChangeCategory.UNKNOWN,
                false,
                true,
                List.of("No category rule matched"),
                ClassificationSource.RULES,
                AiStatus.NOT_REQUESTED,
                null,
                null,
                true
        );
    }
}
