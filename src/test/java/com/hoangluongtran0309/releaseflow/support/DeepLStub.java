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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A local HTTP server standing in for DeepL's {@code POST /v2/translate}. By default it
 * "translates" each text by prefixing the target language, such as {@code [VI] text}.
 */
public final class DeepLStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile int failureStatus = 500;
    private volatile boolean dropOne;

    private DeepLStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static DeepLStub start() {
        return new DeepLStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The next {@code times} requests fail with this HTTP status. */
    public void fail(int status, int times) {
        failureStatus = status;
        failuresLeft.set(times);
    }

    /** Answers with one translation fewer than the texts it was sent. */
    public void dropOneTranslation() {
        dropOne = true;
    }

    public void reset() {
        requests.clear();
        failuresLeft.set(0);
        dropOne = false;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    /** Every text sent for translation, in order. */
    public List<String> translatedTexts() {
        return requests.stream().flatMap(request -> request.texts().stream()).toList();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        JsonNode body = OBJECT_MAPPER.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        List<String> texts = new ArrayList<>();
        body.path("text").values().forEach(text -> texts.add(text.stringValue()));
        String target = body.path("target_lang").stringValue();
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body.path("source_lang").stringValue(),
                target,
                List.copyOf(texts)
        ));
        if (failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
            send(exchange, failureStatus, "{\"message\":\"stubbed failure\"}");
            return;
        }
        List<Map<String, String>> translations = new ArrayList<>();
        for (String text : texts) {
            translations.add(Map.of("detected_source_language", "EN", "text", "[" + target.toUpperCase(Locale.ROOT) + "] " + text));
        }
        if (dropOne && !translations.isEmpty()) {
            translations.removeLast();
        }
        send(exchange, 200, OBJECT_MAPPER.writeValueAsString(Map.of("translations", translations)));
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    public record RecordedRequest(
            String method,
            String path,
            String authorization,
            String sourceLanguage,
            String targetLanguage,
            List<String> texts
    ) {
    }
}
