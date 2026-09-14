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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A local HTTP server standing in for the GitHub REST API: the pull request access
 * check and the paginated pull request file list.
 */
public final class GitHubStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern FILES = Pattern.compile("/repos/[^/]+/[^/]+/pulls/\\d+/files");
    private static final Pattern PULLS = Pattern.compile("/repos/[^/]+/[^/]+/pulls");
    private static final Pattern PAGE = Pattern.compile("(?:^|&)page=(\\d+)");
    private static final int PAGE_SIZE = 100;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile List<Map<String, Object>> files = List.of();
    private volatile Failure filesFailure;
    private volatile Failure accessFailure;
    private volatile Duration delay = Duration.ZERO;

    private GitHubStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static GitHubStub start() {
        return new GitHubStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Every pull request reports these files as modified. */
    public void respondWithFiles(String... paths) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String path : paths) {
            list.add(Map.of("filename", path, "status", "modified"));
        }
        files = List.copyOf(list);
        filesFailure = null;
    }

    public void respondWithRename(String previousPath, String path) {
        files = List.of(Map.of("filename", path, "previous_filename", previousPath, "status", "renamed"));
        filesFailure = null;
    }

    public void respondWithFileCount(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            list.add(Map.of("filename", "src/main/java/File" + index + ".java", "status", "added"));
        }
        files = List.copyOf(list);
        filesFailure = null;
    }

    public void failFiles(int status) {
        filesFailure = new Failure(status, Map.of());
    }

    public void failFiles(int status, Map<String, String> headers) {
        filesFailure = new Failure(status, headers);
    }

    public void failAccessCheck(int status) {
        accessFailure = new Failure(status, Map.of());
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public List<RecordedRequest> fileRequests() {
        return requests.stream().filter(request -> FILES.matcher(request.path()).matches()).toList();
    }

    public void reset() {
        requests.clear();
        files = List.of();
        filesFailure = null;
        accessFailure = null;
        delay = Duration.ZERO;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery();
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                path,
                query,
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Accept"),
                exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version")
        ));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }

        if (FILES.matcher(path).matches()) {
            Failure failure = filesFailure;
            if (failure != null) {
                send(exchange, failure.status(), failure.headers(), "{\"message\":\"stubbed failure\"}");
                return;
            }
            send(exchange, 200, Map.of(), OBJECT_MAPPER.writeValueAsString(page(query)));
        } else if (PULLS.matcher(path).matches()) {
            Failure failure = accessFailure;
            if (failure != null) {
                send(exchange, failure.status(), failure.headers(), "{\"message\":\"Bad credentials\"}");
                return;
            }
            send(exchange, 200, Map.of(), "[]");
        } else {
            send(exchange, 404, Map.of(), "{\"message\":\"Not Found\"}");
        }
    }

    private List<Map<String, Object>> page(String query) {
        Matcher matcher = PAGE.matcher(query);
        int page = matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
        List<Map<String, Object>> all = files;
        int from = Math.min(all.size(), (page - 1) * PAGE_SIZE);
        int to = Math.min(all.size(), from + PAGE_SIZE);
        return all.subList(from, to);
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

    public record RecordedRequest(
            String method,
            String path,
            String query,
            String authorization,
            String accept,
            String apiVersion
    ) {
    }

    private record Failure(int status, Map<String, String> headers) {
    }
}
