package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * The addresses a Microsoft Teams action may post to. The callback URL a Workflows flow
 * shows is both the destination and the credential — its query carries the signature that
 * authorises the call — so it is the one delivery address ReleaseFlow takes from a
 * request, and it must be exactly what Power Automate hands out and nothing else.
 *
 * <p>There is no deployment allowlist to consult, as there is for Slack: the host label
 * belongs to the customer's own environment, so a list of origins could not name it in
 * advance. The shape is the allowlist. It is checked when the action is written and again
 * before every delivery.
 */
@Component
class TeamsWebhookUrl {

    private static final String ENVIRONMENT_SUFFIX = ".environment.api.powerplatform.com";
    private static final String PATH_PREFIX = "/powerautomate/automations/direct/";
    private static final String SCALE_UNIT = "cu/";
    private static final String WORKFLOWS = "workflows/";
    private static final String TRIGGER_SUFFIX = "/triggers/manual/paths/invoke";
    private static final String SIGNATURE = "sig";
    private static final int MAX_LENGTH = 2048;

    /** The submitted callback URL, or an exception saying why it is not one. */
    URI validated(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.teamsWebhookRequired();
        }
        if (value.length() > MAX_LENGTH) {
            throw AutomationActionInvalidException.teamsWebhookInvalid();
        }
        final URI uri;
        try {
            uri = new URI(value.strip());
        } catch (URISyntaxException exception) {
            throw AutomationActionInvalidException.teamsWebhookInvalid();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean shaped = "https".equalsIgnoreCase(uri.getScheme() == null ? "" : uri.getScheme())
                && uri.getUserInfo() == null
                && uri.getFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                // One label beneath the environment domain: the domain itself is nobody's.
                && host.endsWith(ENVIRONMENT_SUFFIX)
                && host.length() > ENVIRONMENT_SUFFIX.length()
                && isWorkflowPath(uri.getPath())
                && isSigned(uri.getRawQuery());
        if (!shaped) {
            throw AutomationActionInvalidException.teamsWebhookInvalid();
        }
        return uri;
    }

    /**
     * {@code /powerautomate/automations/direct/workflows/{id}/triggers/manual/paths/invoke},
     * optionally with the {@code cu/{unit}/} scale-unit segment newer flows carry, and
     * optionally with a trailing slash.
     */
    private static boolean isWorkflowPath(String path) {
        if (path == null || !path.startsWith(PATH_PREFIX)) {
            return false;
        }
        String rest = path.substring(PATH_PREFIX.length());
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        if (rest.startsWith(SCALE_UNIT)) {
            int end = rest.indexOf('/', SCALE_UNIT.length());
            if (end < 0 || !isDigits(rest.substring(SCALE_UNIT.length(), end))) {
                return false;
            }
            rest = rest.substring(end + 1);
        }
        if (!rest.startsWith(WORKFLOWS) || !rest.endsWith(TRIGGER_SUFFIX)) {
            return false;
        }
        String workflowId = rest.substring(WORKFLOWS.length(), rest.length() - TRIGGER_SUFFIX.length());
        return !workflowId.isEmpty() && isWorkflowId(workflowId);
    }

    /**
     * The query must carry a {@code sig} parameter with a value. Split and walked rather
     * than matched, so no pattern is ever asked to back-track over an address somebody
     * else chose.
     */
    private static boolean isSigned(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        for (String parameter : query.split("&")) {
            int equals = parameter.indexOf('=');
            if (equals == SIGNATURE.length()
                    && parameter.regionMatches(0, SIGNATURE, 0, SIGNATURE.length())
                    && equals < parameter.length() - 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDigits(String value) {
        if (value.isEmpty()) {
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

    private static boolean isWorkflowId(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lower = character >= 'a' && character <= 'z';
            boolean upper = character >= 'A' && character <= 'Z';
            if (!digit && !lower && !upper && character != '-') {
                return false;
            }
        }
        return true;
    }
}
