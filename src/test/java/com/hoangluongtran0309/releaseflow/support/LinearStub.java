package com.hoangluongtran0309.releaseflow.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local HTTP server standing in for Linear's GraphQL API: the team lookup that confirms
 * a token and names its workspace, and the issue lookup that restates a change.
 */
public final class LinearStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile Map<String, Object> team;
    private volatile Map<String, Object> issue;
    private volatile Failure failure;
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile Duration delay = Duration.ZERO;
    private volatile String redirectTo;

    private LinearStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static LinearStub start() {
        return new LinearStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The team this token can read, and the workspace it belongs to. */
    public void respondWithTeam(String teamId, String workspaceId) {
        team = Map.of("id", teamId, "organization", Map.of("id", workspaceId));
    }

    /** Linear answers a query it cannot satisfy with a null field, not an HTTP error. */
    public void respondWithNoTeam() {
        team = null;
    }

    public void respondWithIssue(
            String issueId,
            String title,
            String description,
            String authorName,
            String url,
            String teamId,
            String workspaceId
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", issueId);
        value.put("title", title);
        value.put("description", description);
        value.put("url", url);
        value.put("creator", Map.of("name", authorName));
        value.put("team", Map.of("id", teamId, "organization", Map.of("id", workspaceId)));
        issue = value;
    }

    public void respondWithNoIssue() {
        issue = null;
    }

    /** The next {@code times} GraphQL requests fail with this status. */
    public void fail(int status, int times) {
        failure = new Failure(status);
        failuresLeft.set(times);
    }

    /** Answer every request with a redirect, which the client must not follow. */
    public void redirectTo(String location) {
        this.redirectTo = location;
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        team = null;
        issue = null;
        failure = null;
        failuresLeft.set(0);
        delay = Duration.ZERO;
        redirectTo = null;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode request = body.isBlank() ? null : OBJECT_MAPPER.readTree(body);
        String query = request == null ? "" : request.path("query").asString("");
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                query,
                request == null ? "" : request.path("variables").path("id").asString("")
        ));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }

        String redirect = redirectTo;
        if (redirect != null) {
            send(exchange, 302, Map.of("Location", redirect), "");
            return;
        }
        Failure current = failure;
        if (current != null && failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            send(exchange, current.status(), Map.of(), "{\"errors\":[{\"message\":\"stubbed failure\"}]}");
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        if (query.contains("team(id:")) {
            data.put("team", team);
        } else {
            data.put("issue", issue);
        }
        send(exchange, 200, Map.of(), OBJECT_MAPPER.writeValueAsString(Map.of("data", data)));
    }

    private static void send(HttpExchange exchange, int status, Map<String, String> headers, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        new LinkedHashMap<>(headers).forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    public record RecordedRequest(String method, String path, String authorization, String query, String variableId) {
    }

    private record Failure(int status) {
    }
}
