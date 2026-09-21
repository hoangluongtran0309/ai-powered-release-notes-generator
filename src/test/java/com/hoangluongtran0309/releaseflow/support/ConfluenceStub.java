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
 * A local HTTP server standing in for Confluence Cloud. An Action still names a real
 * {@code atlassian.net} site, because the site rules are checked before every delivery;
 * the deployment's stand-in base URL is what sends the call here instead.
 */
public final class ConfluenceStub implements AutoCloseable {

    public static final String SITE = "https://acme.atlassian.net";
    public static final String PAGE_ID = "65601";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile Duration delay = Duration.ZERO;
    private volatile String redirectTo;

    private ConfluenceStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static ConfluenceStub start() {
        return new ConfluenceStub();
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
        status = 200;
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
                exchange.getRequestHeaders().getFirst("Authorization"),
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
        byte[] response = (status == 200
                ? "{\"id\":\"" + PAGE_ID + "\",\"title\":\"Release v1.2.0\"}"
                : "{\"errors\":[{\"status\":" + status + "}]}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    public record RecordedRequest(String method, String path, String authorization, String body) {
    }
}
