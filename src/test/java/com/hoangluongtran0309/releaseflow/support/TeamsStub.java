package com.hoangluongtran0309.releaseflow.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local HTTP server standing in for a Microsoft Teams Workflows callback. An action
 * still holds a real Power Platform URL, because the callback rules are checked before
 * every delivery; the deployment's stand-in base URL is what sends the call here, keeping
 * the path and the signature.
 */
public final class TeamsStub implements AutoCloseable {

    /** A callback URL of the shape Workflows hands out, for an action to hold. */
    public static final String CALLBACK = "https://contoso.environment.api.powerplatform.com"
            + "/powerautomate/automations/direct/workflows/2f1a6c/triggers/manual/paths/invoke"
            + "?api-version=1&sig=uT8k_signature-value";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 202;
    private volatile Duration delay = Duration.ZERO;
    private volatile String redirectTo;

    private TeamsStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static TeamsStub start() {
        return new TeamsStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void respondWith(int status) {
        this.status = status;
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public void redirectTo(String location) {
        redirectTo = location;
    }

    public void reset() {
        requests.clear();
        status = 202;
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
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                body
        ));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        if (redirectTo != null) {
            exchange.getResponseHeaders().add("Location", redirectTo);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    public record RecordedRequest(String method, String path, String query, String body) {
    }
}
