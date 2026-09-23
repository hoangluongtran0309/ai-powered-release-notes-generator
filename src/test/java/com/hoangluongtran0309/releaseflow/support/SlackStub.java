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
 * A local HTTP server standing in for a Slack incoming webhook. Automation only ever
 * posts to Slack's own hosts, so tests reach this stub by pointing the executor's
 * client at it rather than by storing its address as a webhook URL.
 */
public final class SlackStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile Duration delay = Duration.ZERO;

    private SlackStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static SlackStub start() {
        return new SlackStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    public void respondWith(int status) {
        this.status = status;
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public void reset() {
        messages.clear();
        status = 200;
        delay = Duration.ZERO;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        int answer = status;
        if (answer == 200) {
            Map<?, ?> payload = OBJECT_MAPPER.readValue(body, Map.class);
            messages.add(String.valueOf(payload.get("text")));
        }
        byte[] response = (answer == 200 ? "ok" : "invalid_payload").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
