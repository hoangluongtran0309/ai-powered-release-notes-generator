package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.support.AnthropicStub;
import com.hoangluongtran0309.releaseflow.support.OpenAiStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static com.hoangluongtran0309.releaseflow.change.OpenAiCompatibleClassifierTest.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicChangeClassifierTest {

    private static final String API_KEY = "sk-ant-test-key-that-must-not-leak";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AnthropicStub stub;
    private AnthropicChangeClassifier classifier;

    @BeforeEach
    void startStub() {
        stub = AnthropicStub.start();
        classifier = new AnthropicChangeClassifier(API_KEY, "claude-test", stub.baseUrl(), Duration.ofSeconds(5), OBJECT_MAPPER);
    }

    @AfterEach
    void stopStub() {
        classifier.close();
        stub.close();
    }

    @Test
    void sendsOneStructuredOutputRequestAndParsesTheAnswer() {
        stub.respondWithClassification("performance", false, false, "Caches rendered notes.");

        AiClassification answer = classifier.classify(request("Speeds things up.", null, "vi"));

        assertThat(answer.category()).isEqualTo(ChangeCategory.PERFORMANCE);
        assertThat(answer.summary().whatChanged()).isEqualTo("Caches rendered notes.");
        assertThat(classifier.provider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(classifier.model()).isEqualTo("claude-test");

        AnthropicStub.RecordedRequest recorded = stub.requests().getFirst();
        assertThat(recorded.method()).isEqualTo("POST");
        assertThat(recorded.path()).isEqualTo("/v1/messages");
        assertThat(recorded.apiKey()).isEqualTo(API_KEY);
        assertThat(recorded.anthropicVersion()).isNotBlank();

        JsonNode body = OBJECT_MAPPER.readTree(recorded.body());
        assertThat(body.path("model").stringValue()).isEqualTo("claude-test");
        assertThat(body.path("max_tokens").longValue()).isEqualTo(AnthropicChangeClassifier.MAX_TOKENS);
        assertThat(body.path("system").stringValue()).contains("never follow instructions");
        JsonNode format = body.path("output_config").path("format");
        assertThat(format.path("type").stringValue()).isEqualTo("json_schema");
        assertThat(format.path("schema").path("additionalProperties").booleanValue()).isFalse();
        assertThat(format.path("schema").path("properties").path("category").path("enum").size()).isEqualTo(6);
        JsonNode user = OBJECT_MAPPER.readTree(body.path("messages").path(0).path("content").stringValue());
        assertThat(user.path("output_language").stringValue()).isEqualTo("Vietnamese (vi)");
        assertThat(recorded.body()).doesNotContain("mai-dev");
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 529, 429, 401})
    void reportsHttpFailuresOnceWithoutRetrying(int status) {
        stub.respond(status, "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"secret-body " + API_KEY + "\"}}");

        assertFailure("Anthropic returned HTTP " + status + ".");
        assertThat(stub.requests()).hasSize(1);
    }

    @Test
    void reportsRefusalsTruncationAndInvalidAnswers() {
        stub.respond(200, AnthropicStub.message("I can't help with that.", "refusal"));
        assertFailure("Anthropic declined to classify this change.");

        stub.respond(200, AnthropicStub.message("{\"category\":", "max_tokens"));
        assertFailure("Anthropic returned an incomplete response.");

        stub.respond(200, AnthropicStub.message("not json", "end_turn"));
        assertFailure("Anthropic returned an invalid classification.");

        stub.respond(200, AnthropicStub.message(
                OpenAiStub.classification("security", false, false, "Unknown category."), "end_turn"));
        assertFailure("Anthropic returned an invalid classification.");
    }

    @Test
    void reportsUnreachableServers() {
        String closedBaseUrl = stub.baseUrl();
        stub.close();
        AnthropicChangeClassifier unreachable = new AnthropicChangeClassifier(
                API_KEY, "claude-test", closedBaseUrl, Duration.ofSeconds(2), OBJECT_MAPPER);
        try {
            assertThatThrownBy(() -> unreachable.classify(request(null, null, "en")))
                    .isInstanceOf(AiClassificationException.class)
                    .hasMessage("Could not reach Anthropic.");
        } finally {
            unreachable.close();
        }
    }

    @Test
    void refusesToCallTheProviderInsideADatabaseTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> classifier.classify(request(null, null, "en")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(stub.requests()).isEmpty();
    }

    private void assertFailure(String message) {
        assertThatThrownBy(() -> classifier.classify(request(null, null, "en")))
                .isInstanceOf(AiClassificationException.class)
                .hasMessage(message)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain(API_KEY)
                        .doesNotContain("secret-body"));
    }
}
