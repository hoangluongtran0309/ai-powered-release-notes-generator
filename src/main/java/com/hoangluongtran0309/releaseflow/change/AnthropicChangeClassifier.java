package com.hoangluongtran0309.releaseflow.change;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The Claude Messages API through the official Java SDK, with Structured Outputs
 * ({@code output_config.format}) enforcing the shared response schema. The SDK's
 * automatic retries are switched off so each change costs exactly one request.
 */
final class AnthropicChangeClassifier implements AiChangeClassifier, AutoCloseable {

    static final long MAX_TOKENS = 16000L;
    static final String INVALID = "Anthropic returned an invalid classification.";

    private static final Logger log = LoggerFactory.getLogger(AnthropicChangeClassifier.class);

    private final String model;
    private final Duration timeout;
    private final AnthropicClient client;
    private final ObjectMapper objectMapper;
    private final AiClassificationParser parser;

    AnthropicChangeClassifier(String apiKey, String model, String baseUrl, Duration timeout, ObjectMapper objectMapper) {
        this.model = model;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.parser = new AiClassificationParser(objectMapper);
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .timeout(timeout)
                .maxRetries(0)
                .build();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.ANTHROPIC;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AiClassification classify(AiClassificationRequest request) {
        AiChangeClassifier.requireNoTransaction();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(AiClassificationPrompt.SYSTEM)
                .addUserMessage(AiClassificationPrompt.userMessage(request, objectMapper))
                .outputConfig(OutputConfig.builder()
                        .format(JsonOutputFormat.builder().schema(schema(request)).build())
                        .build())
                .build();

        final Message response;
        try {
            response = client.messages().create(params);
        } catch (AnthropicServiceException exception) {
            throw failure(request, "Anthropic returned HTTP " + exception.statusCode() + ".");
        } catch (AnthropicIoException exception) {
            throw failure(request, causedByTimeout(exception)
                    ? "Anthropic did not respond within " + OpenAiCompatibleClassifier.describe(timeout) + "."
                    : "Could not reach Anthropic.");
        } catch (AnthropicException exception) {
            throw failure(request, "Could not reach Anthropic.");
        }

        // Check why generation stopped before reading any content.
        Optional<StopReason> stopReason = response.stopReason();
        if (stopReason.isPresent() && StopReason.REFUSAL.equals(stopReason.get())) {
            throw failure(request, "Anthropic declined to classify this change.");
        }
        if (stopReason.isEmpty() || !StopReason.END_TURN.equals(stopReason.get())) {
            throw failure(request, "Anthropic returned an incomplete response.");
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElseThrow(() -> failure(request, INVALID));
        try {
            return parser.parse(text, request);
        } catch (AiClassificationException exception) {
            throw failure(request, INVALID);
        }
    }

    // The schema names the Organization's audiences, so it is built for each request.
    private JsonOutputFormat.Schema schema(AiClassificationRequest request) {
        Map<String, Object> fields = objectMapper.convertValue(
                AiClassificationPrompt.responseSchema(request, objectMapper),
                new TypeReference<Map<String, Object>>() {
                }
        );
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        fields.forEach((name, value) -> schema.putAdditionalProperty(name, JsonValue.from(value)));
        return schema.build();
    }

    @Override
    public void close() {
        client.close();
    }

    private static AiClassificationException failure(AiClassificationRequest request, String message) {
        log.warn("Anthropic classification of change {} failed: {}", request.changeId(), message);
        return new AiClassificationException(message);
    }

    private static boolean causedByTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
