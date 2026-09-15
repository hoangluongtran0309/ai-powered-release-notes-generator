package com.hoangluongtran0309.releaseflow.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local HTTP server standing in for the OpenAI Chat Completions API, so tests
 * exercise the real HTTP path without a network or an API key.
 */
public final class OpenAiStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile StubResponse response = new StubResponse(200, completion("feature", false, false, "Adds a capability."), Duration.ZERO);

    private OpenAiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static OpenAiStub start() {
        return new OpenAiStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort() + "/v1";
    }

    public void respond(int status, String body) {
        respond(status, body, Duration.ZERO);
    }

    public void respond(int status, String body, Duration delay) {
        response = new StubResponse(status, body, delay);
    }

    /** A valid answer whose what_changed is {@code summary}; the AI does not ask for review. */
    public void respondWithClassification(String category, boolean breaking, String summary) {
        respondWithClassification(category, breaking, false, summary);
    }

    public void respondWithClassification(String category, boolean breaking, boolean needsReview, String summary) {
        respond(200, completion(category, breaking, needsReview, summary));
    }

    /** A valid answer that also explains the change to the given audiences. */
    public void respondWithNarratives(String category, String summary, Map<String, String> narratives) {
        respond(200, completionWithContent(classification(category, false, false, summary, narratives)));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        respondWithClassification("feature", false, "Adds a capability.");
    }

    public static String completion(String category, boolean breaking, boolean needsReview, String summary) {
        return completionWithContent(classification(category, breaking, needsReview, summary));
    }

    public static String classification(String category, boolean breaking, boolean needsReview, String summary) {
        return classification(category, breaking, needsReview, summary, Map.of());
    }

    public static String classification(
            String category,
            boolean breaking,
            boolean needsReview,
            String summary,
            Map<String, String> narratives
    ) {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "category", category,
                "breaking_change", breaking,
                "needs_human_review", needsReview,
                "neutral_core", Map.of(
                        "what_changed", summary,
                        "why_changed", "Requested by users.",
                        "technical_detail", "Handled in the exporter.",
                        "migration_step", ""
                ),
                "narratives", narratives
        ));
    }

    public static String completionWithContent(String content) {
        return OBJECT_MAPPER.writeValueAsString(Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "choices", List.of(Map.of(
                        "index", 0,
                        "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", content)
                ))
        ));
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body
        ));
        StubResponse current = response;
        try {
            Thread.sleep(current.delay());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        byte[] payload = current.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(current.status(), payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    public record RecordedRequest(String method, String path, String authorization, String body) {
    }

    private record StubResponse(int status, String body, Duration delay) {
    }
}
