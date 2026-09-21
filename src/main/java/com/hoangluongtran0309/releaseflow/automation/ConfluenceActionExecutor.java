package com.hoangluongtran0309.releaseflow.automation;

import com.hoangluongtran0309.releaseflow.audience.MarkdownHtml;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates a page in a Confluence Cloud space. The site is the only address an Action
 * carries, so it is checked again here before every delivery; a deployment may send the
 * calls elsewhere for a local stand-in, but the links ReleaseFlow records are always
 * built from the site itself.
 *
 * <p>The page carries a marker naming the Action Run that wrote it. Nothing reads it
 * back — Confluence will happily create a second page with the same title — so it is
 * there to say where a page came from when somebody confirms a repeat.
 */
@Component
class ConfluenceActionExecutor implements RuleActionExecutor {

    static final String EMAIL_KEY = "email";
    static final String SPACE_KEY = "spaceId";
    static final String PARENT_KEY = "parentPageId";
    static final int MAX_STORAGE_BYTES = 2_000_000;
    static final String CONTENT_TOO_LARGE = "confluence_content_too_large";
    static final String REJECTED = "confluence_rejected";
    static final String CONFIGURATION_INVALID = "confluence_configuration_invalid";

    private static final Logger log = LoggerFactory.getLogger(ConfluenceActionExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ConfluenceSite sites;
    private final String apiBaseUrl;

    ConfluenceActionExecutor(
            ObjectMapper objectMapper,
            ConfluenceSite sites,
            @Value("${releaseflow.automation.confluence.api-base-url:}") String apiBaseUrl,
            @Value("${releaseflow.automation.timeout}") Duration timeout
    ) {
        this.objectMapper = objectMapper;
        this.sites = sites;
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? null
                : withoutTrailingSlashes(apiBaseUrl.strip());
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(timeout)
                        // A redirect proves nothing about who answered, so it is never followed.
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public ActionType actionType() {
        return ActionType.CONFLUENCE;
    }

    @Override
    public void validate(Map<String, String> configuration, String rawSecret) {
        target(configuration);
        if (rawSecret == null || rawSecret.isBlank()) {
            throw AutomationActionInvalidException.confluenceTokenRequired();
        }
    }

    @Override
    public ActionResult execute(ActionCommand command) {
        requireNoTransaction();
        final Target target;
        try {
            validate(command.configuration(), command.rawSecret());
            target = target(command.configuration());
        } catch (AutomationActionInvalidException exception) {
            return ActionResult.failed(CONFIGURATION_INVALID);
        }
        String storage = MarkdownHtml.render(command.noteContent()) + "\n" + marker(command.actionRunId());
        if (storage.getBytes(StandardCharsets.UTF_8).length > MAX_STORAGE_BYTES) {
            return ActionResult.failed(CONTENT_TOO_LARGE);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("spaceId", target.spaceId());
        body.put("status", "current");
        body.put("title", "Release " + command.releaseVersion());
        if (target.parentPageId() != null) {
            body.put("parentId", target.parentPageId());
        }
        body.put("body", Map.of("representation", "storage", "value", storage));
        try {
            ResponseEntity<String> created = restClient.post()
                    .uri(callAddress(target) + "/wiki/api/v2/pages")
                    .headers(headers -> headers.setBasicAuth(
                            target.email(), command.rawSecret(), StandardCharsets.UTF_8))
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            if (created.getStatusCode().is2xxSuccessful()) {
                return ActionResult.succeeded(pageUrl(target, created.getBody()));
            }
            // A redirect is not followed, so no page was written wherever it pointed.
            log.warn("Confluence answered HTTP {} for the note of release {}.",
                    created.getStatusCode().value(), command.releaseId());
            return ActionResult.failed(REJECTED);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (exception.getStatusCode().is5xxServerError()) {
                // Confluence may have created the page before it failed to answer.
                log.warn("Confluence answered HTTP {} for the note of release {}.", status, command.releaseId());
                return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
            }
            log.warn("Confluence refused the note of release {} with HTTP {}.", command.releaseId(), status);
            return ActionResult.failed(REJECTED);
        } catch (RestClientException exception) {
            // Confluence may have created the page before the connection failed.
            log.warn("Could not reach Confluence with the note of release {}.", command.releaseId());
            return ActionResult.unknown(ActionResult.OUTCOME_UNKNOWN);
        }
    }

    /** Where the call goes: the site itself, unless the deployment stands in for Confluence. */
    private String callAddress(Target target) {
        return apiBaseUrl == null ? target.site() : apiBaseUrl;
    }

    /** The page as a person would open it, always on the real site. */
    private String pageUrl(Target target, String body) {
        try {
            JsonNode page = objectMapper.readTree(body == null ? "" : body);
            String id = page == null ? "" : page.path("id").asString("");
            return id.isBlank()
                    ? null
                    : target.site() + "/wiki/spaces/" + target.spaceId() + "/pages/" + id;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private Target target(Map<String, String> configuration) {
        return new Target(
                sites.validated(configuration.get(ConfluenceSite.KEY)),
                email(configuration.get(EMAIL_KEY)),
                space(configuration.get(SPACE_KEY)),
                parent(configuration.get(PARENT_KEY))
        );
    }

    private static String email(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.confluenceEmailRequired();
        }
        try {
            InternetAddress address = new InternetAddress(value.strip(), true);
            address.validate();
            return address.getAddress();
        } catch (AddressException exception) {
            throw AutomationActionInvalidException.confluenceEmailInvalid();
        }
    }

    private static String space(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.confluenceSpaceRequired();
        }
        String identifier = value.strip();
        if (!isNumeric(identifier)) {
            throw AutomationActionInvalidException.confluenceSpaceInvalid();
        }
        return identifier;
    }

    private static String parent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String identifier = value.strip();
        if (!isNumeric(identifier)) {
            throw AutomationActionInvalidException.confluenceParentInvalid();
        }
        return identifier;
    }

    // Walked rather than matched, so no pattern is ever asked to back-track over a
    // string somebody else chose.
    private static boolean isNumeric(String value) {
        if (value.length() > 32) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static String withoutTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String marker(UUID actionRunId) {
        return "<!-- releaseflow-action:" + actionRunId + " -->";
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Confluence must not be called inside a database transaction.");
        }
    }

    /** One Confluence destination, as the Action's configuration describes it. */
    private record Target(String site, String email, String spaceId, String parentPageId) {
    }
}
