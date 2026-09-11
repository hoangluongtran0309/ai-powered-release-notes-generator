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
    private volatile StubResponse response = new StubResponse(200, completion("feature", false, "Adds a capability."), Duration.ZERO);

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

    public void respondWithClassification(String category, boolean breaking, String rationale) {
        respond(200, completion(category, breaking, rationale));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        respondWithClassification("feature", false, "Adds a capability.");
    }

    public static String completion(String category, boolean breaking, String rationale) {
        String content = OBJECT_MAPPER.writeValueAsString(Map.of(
                "category", category,
                "breaking", breaking,
                "rationale", rationale
        ));
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
