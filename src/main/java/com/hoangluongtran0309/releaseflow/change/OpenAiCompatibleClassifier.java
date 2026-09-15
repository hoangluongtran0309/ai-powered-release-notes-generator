package com.hoangluongtran0309.releaseflow.change;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * The Chat Completions API as served by OpenAI and by DeepSeek. OpenAI enforces the
 * response schema (Structured Outputs); DeepSeek only guarantees a JSON object, so
 * the schema is spelled out in the prompt and checked by the shared parser.
 */
final class OpenAiCompatibleClassifier implements AiChangeClassifier {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClassifier.class);

    private final AiProvider provider;
    private final String model;
    private final Duration timeout;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiClassificationParser parser;

    private OpenAiCompatibleClassifier(
            AiProvider provider,
            String apiKey,
            String model,
            String baseUrl,
            Duration timeout,
            ObjectMapper objectMapper
    ) {
        this.provider = provider;
        this.model = model;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.parser = new AiClassificationParser(objectMapper);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    static OpenAiCompatibleClassifier openAi(
            String apiKey,
            String model,
            String baseUrl,
            Duration timeout,
            ObjectMapper objectMapper
    ) {
        return new OpenAiCompatibleClassifier(AiProvider.OPENAI, apiKey, model, baseUrl, timeout, objectMapper);
    }

    static OpenAiCompatibleClassifier deepSeek(
            String apiKey,
            String model,
            String baseUrl,
            Duration timeout,
            ObjectMapper objectMapper
    ) {
        return new OpenAiCompatibleClassifier(AiProvider.DEEPSEEK, apiKey, model, baseUrl, timeout, objectMapper);
    }

    @Override
    public AiProvider provider() {
        return provider;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public AiClassification classify(AiClassificationRequest request) {
        AiChangeClassifier.requireNoTransaction();
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw failure(request, provider.getLabel() + " returned HTTP " + exception.getStatusCode().value() + ".");
        } catch (ResourceAccessException exception) {
            throw failure(request, causedByTimeout(exception)
                    ? provider.getLabel() + " did not respond within " + describe(timeout) + "."
                    : "Could not reach " + provider.getLabel() + ".");
        } catch (RestClientException exception) {
            throw failure(request, "Could not reach " + provider.getLabel() + ".");
        }
        return parse(request, responseBody);
    }

    private String requestBody(AiClassificationRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        String system = AiClassificationPrompt.SYSTEM;
        ObjectNode schema = AiClassificationPrompt.responseSchema(request, objectMapper);
        if (provider == AiProvider.OPENAI) {
            body.put("store", false);
            ObjectNode jsonSchema = body.putObject("response_format")
                    .put("type", "json_schema")
                    .putObject("json_schema");
            jsonSchema.put("name", "change_classification");
            jsonSchema.put("strict", true);
            jsonSchema.set("schema", schema);
        } else {
            body.putObject("response_format").put("type", "json_object");
            system = system + "\nRespond with JSON only, matching this JSON schema:\n" + objectMapper.writeValueAsString(schema);
        }
        body.putArray("messages")
                .add(objectMapper.createObjectNode().put("role", "system").put("content", system))
                .add(objectMapper.createObjectNode()
                        .put("role", "user")
                        .put("content", AiClassificationPrompt.userMessage(request, objectMapper)));
        return objectMapper.writeValueAsString(body);
    }

    private AiClassification parse(AiClassificationRequest request, String responseBody) {
        final JsonNode choice;
        try {
            choice = objectMapper.readTree(responseBody == null ? "" : responseBody).path("choices").path(0);
        } catch (JacksonException exception) {
            throw invalid(request);
        }
        JsonNode message = choice.path("message");
        if (message.path("refusal").isString() && !message.path("refusal").stringValue().isBlank()) {
            throw failure(request, provider.getLabel() + " declined to classify this change.");
        }
        if (!"stop".equals(choice.path("finish_reason").asString(""))) {
            throw failure(request, provider.getLabel() + " returned an incomplete response.");
        }
        JsonNode content = message.path("content");
        if (!content.isString()) {
            throw invalid(request);
        }
        try {
            return parser.parse(content.stringValue(), request);
        } catch (AiClassificationException exception) {
            throw invalid(request);
        }
    }

    private AiClassificationException invalid(AiClassificationRequest request) {
        return failure(request, provider.getLabel() + " returned an invalid classification.");
    }

    private AiClassificationException failure(AiClassificationRequest request, String message) {
        log.warn("{} classification of change {} failed: {}", provider.getLabel(), request.changeId(), message);
        return new AiClassificationException(message);
    }

    private static boolean causedByTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }

    static String describe(Duration duration) {
        return duration.toMillis() % 1000 == 0 ? duration.toSeconds() + " seconds" : duration.toMillis() + " ms";
    }
}
