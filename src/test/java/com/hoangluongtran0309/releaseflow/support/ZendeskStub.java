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
 * A local HTTP server standing in for a Zendesk help centre. It answers both calls a
 * delivery makes — the OAuth token and the article — and each can be made to fail on its
 * own, because the two are allowed to fail differently.
 */
public final class ZendeskStub implements AutoCloseable {

    public static final String SUBDOMAIN = "acme";
    public static final String SECTION_ID = "360001";
    public static final String CLIENT_ID = "releaseflow";
    public static final String ACCESS_TOKEN = "short-lived-token";
    public static final String ARTICLE_URL = "https://acme.zendesk.com/hc/en-us/articles/123";

    private static final String TOKEN_PATH = "/oauth/tokens";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile int tokenStatus = 200;
    private volatile int articleStatus = 201;
    private volatile boolean tokenWithoutToken;
    private volatile boolean articleWithoutUrl;
    private volatile Duration delay = Duration.ZERO;
    private volatile String redirectTo;

    private ZendeskStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static ZendeskStub start() {
        return new ZendeskStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public List<RecordedRequest> articleRequests() {
        return requests.stream().filter(request -> !TOKEN_PATH.equals(request.path())).toList();
    }

    public void failTokenWith(int status) {
        tokenStatus = status;
    }

    /** A token answer that names no token, which publishes nothing. */
    public void answerTokenWithoutToken() {
        tokenWithoutToken = true;
    }

    public void failArticleWith(int status) {
        articleStatus = status;
    }

    /** An article answer nobody can read an article out of. */
    public void answerArticleWithoutUrl() {
        articleWithoutUrl = true;
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public void redirectTo(String location) {
        redirectTo = location;
    }

    public void reset() {
        requests.clear();
        tokenStatus = 200;
        articleStatus = 201;
        tokenWithoutToken = false;
        articleWithoutUrl = false;
        delay = Duration.ZERO;
        redirectTo = null;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                path,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
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
        if (TOKEN_PATH.equals(path)) {
            send(exchange, tokenStatus, tokenStatus == 200
                    ? (tokenWithoutToken ? "{}" : "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"expires_in\":1800}")
                    : "{\"error\":\"invalid_client\"}");
            return;
        }
        send(exchange, articleStatus, articleStatus / 100 == 2
                ? (articleWithoutUrl
                        ? "{\"article\":{\"id\":123}}"
                        : "{\"article\":{\"id\":123,\"html_url\":\"" + ARTICLE_URL + "\"}}")
                : "{\"error\":\"RecordInvalid\"}");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    public record RecordedRequest(
            String method,
            String path,
            String authorization,
            String contentType,
            String body
    ) {
    }
}
