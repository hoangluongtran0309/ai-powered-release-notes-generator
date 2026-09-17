package com.hoangluongtran0309.releaseflow.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A local HTTP server standing in for Jira Cloud's REST API: the project lookup that
 * confirms an account and token, the JQL search a poll pages through, and the issue
 * lookup that explains a change from another source.
 */
public final class JiraStub implements AutoCloseable {

    /** A site that passes the Atlassian Cloud rules; calls go to this stub instead. */
    public static final String SITE = "https://acme.atlassian.net";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern PROJECT = Pattern.compile("/rest/api/3/project/([^/]+)");
    private static final Pattern ISSUE = Pattern.compile("/rest/api/3/issue/([^/]+)");
    private static final String SEARCH = "/rest/api/3/search/jql";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Map<String, Object>> issues = new ConcurrentHashMap<>();
    private final Set<String> failingIssues = ConcurrentHashMap.newKeySet();
    private volatile List<List<Map<String, Object>>> searchPages = List.of();
    private volatile int projectStatus = 200;
    private volatile Failure searchFailure;
    private final AtomicInteger searchFailuresLeft = new AtomicInteger();
    private volatile String redirectTo;

    private JiraStub() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public static JiraStub start() {
        return new JiraStub();
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** The status the project lookup answers with; 200 confirms the account and token. */
    public void respondToProjectWith(int status) {
        projectStatus = status;
    }

    /** The search answers page by page; every page but the last names the next one. */
    @SafeVarargs
    public final void respondWithSearchPages(List<Map<String, Object>>... pages) {
        searchPages = List.of(pages);
    }

    /** The next {@code times} searches fail with this status and headers. */
    public void failSearch(int status, Map<String, String> headers, int times) {
        searchFailure = new Failure(status, headers);
        searchFailuresLeft.set(times);
    }

    public void respondWithIssue(String key, String summary, String type, String status, String description) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", summary);
        fields.put("description", adf(description));
        fields.put("issuetype", Map.of("name", type));
        fields.put("status", Map.of("name", status));
        issues.put(key, Map.of("id", "10" + key.hashCode(), "key", key, "fields", fields));
    }

    /** Jira answers this issue with a server error, as if it were briefly unavailable. */
    public void failIssue(String key) {
        failingIssues.add(key);
    }

    public void redirectTo(String location) {
        redirectTo = location;
    }

    /** A search result: an issue that is done, with the times Jira reports. */
    public static Map<String, Object> doneIssue(
            String id,
            String key,
            String summary,
            String reporter,
            Instant updated,
            Instant resolved
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("summary", summary);
        fields.put("description", adf("Details of " + key));
        fields.put("reporter", Map.of("displayName", reporter));
        fields.put("updated", updated.toString());
        fields.put("resolutiondate", resolved == null ? null : resolved.toString());
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("id", id);
        issue.put("key", key);
        issue.put("fields", fields);
        return issue;
    }

    private static Map<String, Object> adf(String text) {
        return Map.of("type", "doc", "version", 1, "content", List.of(
                Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text)))
        ));
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public List<RecordedRequest> searchRequests() {
        return requests.stream().filter(request -> SEARCH.equals(request.path())).toList();
    }

    public List<RecordedRequest> issueRequests() {
        return requests.stream().filter(request -> ISSUE.matcher(request.path()).matches()).toList();
    }

    public void reset() {
        requests.clear();
        issues.clear();
        failingIssues.clear();
        searchPages = List.of();
        projectStatus = 200;
        searchFailure = null;
        searchFailuresLeft.set(0);
        redirectTo = null;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        requests.add(new RecordedRequest(
                exchange.getRequestMethod(),
                path,
                query,
                exchange.getRequestHeaders().getFirst("Authorization")
        ));

        if (redirectTo != null) {
            send(exchange, 302, Map.of("Location", redirectTo), "");
            return;
        }
        Matcher project = PROJECT.matcher(path);
        Matcher issue = ISSUE.matcher(path);
        if (project.matches()) {
            send(exchange, projectStatus, Map.of(), projectStatus == 200
                    ? OBJECT_MAPPER.writeValueAsString(Map.of("id", "10000", "key", project.group(1)))
                    : "{\"errorMessages\":[\"No project could be found\"]}");
        } else if (SEARCH.equals(path)) {
            Failure failure = searchFailure;
            if (failure != null && searchFailuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
                send(exchange, failure.status(), failure.headers(), "{\"errorMessages\":[\"stubbed failure\"]}");
                return;
            }
            String token = query.getOrDefault("nextPageToken", "");
            int index = token.isEmpty() ? 0 : Integer.parseInt(token.substring("page-".length()));
            List<List<Map<String, Object>>> pages = searchPages;
            List<Map<String, Object>> page = index < pages.size() ? pages.get(index) : List.of();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("issues", page);
            if (index + 1 < pages.size()) {
                body.put("nextPageToken", "page-" + (index + 1));
            }
            send(exchange, 200, Map.of(), OBJECT_MAPPER.writeValueAsString(body));
        } else if (issue.matches()) {
            String key = issue.group(1);
            if (failingIssues.contains(key)) {
                send(exchange, 503, Map.of(), "{\"errorMessages\":[\"stubbed failure\"]}");
            } else if (issues.containsKey(key)) {
                send(exchange, 200, Map.of(), OBJECT_MAPPER.writeValueAsString(issues.get(key)));
            } else {
                send(exchange, 404, Map.of(), "{\"errorMessages\":[\"Issue does not exist\"]}");
            }
        } else {
            send(exchange, 404, Map.of(), "{}");
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            values.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static void send(HttpExchange exchange, int status, Map<String, String> headers, String body)
            throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        new LinkedHashMap<>(headers).forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
        if (payload.length > 0) {
            exchange.getResponseBody().write(payload);
        }
        exchange.close();
    }

    public record RecordedRequest(String method, String path, Map<String, String> query, String authorization) {
    }

    private record Failure(int status, Map<String, String> headers) {
    }
}
