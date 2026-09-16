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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A local HTTP server standing in for the GitLab REST v4 API: the project access check,
 * the paginated merge request diffs, and the merged merge request list.
 */
public final class GitLabStub implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern DIFFS = Pattern.compile("/api/v4/projects/[^/]+/merge_requests/\\d+/diffs");
    private static final Pattern MERGE_REQUESTS = Pattern.compile("/api/v4/projects/[^/]+/merge_requests");
    private static final Pattern PROJECT = Pattern.compile("/api/v4/projects/[^/]+");
    private static final Pattern PAGE = Pattern.compile("(?:^|&)page=(\\d+)");
    private static final int PAGE_SIZE = 100;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile List<Map<String, Object>> diffs = List.of();
    private volatile Failure diffsFailure;
    private volatile Failure projectFailure;
    private volatile List<Map<String, Object>> mergeRequests = List.of();
    private volatile Failure listFailure;
    private final AtomicInteger listFailuresLeft = new AtomicInteger();
    private volatile Duration delay = Duration.ZERO;
    private volatile String redirectTo;

    private GitLabStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static GitLabStub start() {
        return new GitLabStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The host and port a deployment must allow for this stub to be reachable. */
    public String allowedOrigin() {
        return baseUrl();
    }

    /** Every merge request reports these files as modified. */
    public void respondWithFiles(String... paths) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String path : paths) {
            list.add(diff(path, path, Map.of()));
        }
        diffs = List.copyOf(list);
        diffsFailure = null;
    }

    public void respondWithRename(String previousPath, String path) {
        diffs = List.of(diff(previousPath, path, Map.of("renamed_file", true)));
        diffsFailure = null;
    }

    public void respondWithDiffs(List<Map<String, Object>> entries) {
        diffs = List.copyOf(entries);
        diffsFailure = null;
    }

    public void respondWithFileCount(int count) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String path = "src/main/java/File" + index + ".java";
            list.add(diff(path, path, Map.of("new_file", true)));
        }
        diffs = List.copyOf(list);
        diffsFailure = null;
    }

    public void failDiffs(int status) {
        diffsFailure = new Failure(status, Map.of());
    }

    public void failProjectCheck(int status) {
        projectFailure = new Failure(status, Map.of());
    }

    /** Answer every request with a redirect, which the client must not follow. */
    public void redirectTo(String location) {
        this.redirectTo = location;
    }

    /**
     * The project's merged merge requests, served least recently updated first, a page at
     * a time, to history imports.
     */
    public void respondWithMergeRequests(List<Map<String, Object>> merged) {
        mergeRequests = merged.stream()
                .sorted(Comparator.comparing((Map<String, Object> mr) -> Instant.parse(mr.get("updated_at").toString())))
                .toList();
    }

    /** The next {@code times} merge request list requests fail with this status and headers. */
    public void failMergeRequestList(int status, Map<String, String> headers, int times) {
        listFailure = new Failure(status, headers);
        listFailuresLeft.set(times);
    }

    /** One merged merge request as the REST list returns it. */
    public static Map<String, Object> mergedMergeRequest(int iid, Instant mergedAt, Instant updatedAt) {
        Map<String, Object> mergeRequest = new LinkedHashMap<>();
        mergeRequest.put("iid", iid);
        mergeRequest.put("title", "feat: change " + iid);
        mergeRequest.put("description", null);
        mergeRequest.put("merged_at", mergedAt == null ? null : mergedAt.toString());
        mergeRequest.put("updated_at", updatedAt.toString());
        mergeRequest.put("merge_commit_sha", "%040x".formatted(iid));
        mergeRequest.put("sha", "%040x".formatted(iid + 1000));
        mergeRequest.put("web_url", "https://gitlab.com/acme/app/-/merge_requests/" + iid);
        mergeRequest.put("author", Map.of("username", "mai-dev"));
        mergeRequest.put("target_branch", "main");
        mergeRequest.put("labels", List.of());
        return mergeRequest;
    }

    /** One entry of the diffs response; {@code extra} sets flags such as {@code too_large}. */
    public static Map<String, Object> diff(String oldPath, String newPath, Map<String, Object> extra) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("old_path", oldPath);
        entry.put("new_path", newPath);
        entry.putAll(extra);
        return entry;
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public List<RecordedRequest> diffRequests() {
        return requests.stream().filter(request -> DIFFS.matcher(request.path()).matches()).toList();
    }

    public List<RecordedRequest> listRequests() {
        return requests.stream()
                .filter(request -> MERGE_REQUESTS.matcher(request.path()).matches())
                .filter(request -> request.query().contains("state=merged"))
                .toList();
    }

    public void delay(Duration delay) {
        this.delay = delay;
    }

    public void reset() {
        requests.clear();
        diffs = List.of();
        diffsFailure = null;
        projectFailure = null;
        mergeRequests = List.of();
        listFailure = null;
        listFailuresLeft.set(0);
        delay = Duration.ZERO;
        redirectTo = null;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery();
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                path,
                query,
                exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"),
                exchange.getRequestHeaders().getFirst("Accept")
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

        if (DIFFS.matcher(path).matches()) {
            Failure failure = diffsFailure;
            if (failure != null) {
                send(exchange, failure.status(), failure.headers(), "{\"message\":\"stubbed failure\"}");
                return;
            }
            send(exchange, 200, Map.of(), OBJECT_MAPPER.writeValueAsString(page(diffs, query)));
        } else if (MERGE_REQUESTS.matcher(path).matches()) {
            Failure failure = listFailure;
            if (failure != null && listFailuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                send(exchange, failure.status(), failure.headers(), "{\"message\":\"stubbed failure\"}");
                return;
            }
            send(exchange, 200, Map.of(), OBJECT_MAPPER.writeValueAsString(page(mergeRequests, query)));
        } else if (PROJECT.matcher(path).matches()) {
            Failure failure = projectFailure;
            if (failure != null) {
                send(exchange, failure.status(), failure.headers(), "{\"message\":\"401 Unauthorized\"}");
                return;
            }
            send(exchange, 200, Map.of(), "{\"path_with_namespace\":\"acme/app\"}");
        } else {
            send(exchange, 404, Map.of(), "{\"message\":\"404 Not Found\"}");
        }
    }

    private static List<Map<String, Object>> page(List<Map<String, Object>> all, String query) {
        Matcher matcher = PAGE.matcher(query);
        int page = matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
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

    public record RecordedRequest(String method, String path, String query, String privateToken, String accept) {
    }

    private record Failure(int status, Map<String, String> headers) {
    }
}
