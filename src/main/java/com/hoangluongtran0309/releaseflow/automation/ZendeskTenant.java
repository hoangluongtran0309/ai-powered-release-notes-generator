package com.hoangluongtran0309.releaseflow.automation;

import java.util.Locale;
import java.util.Map;

/**
 * The help centre a Zendesk action writes to, read from its configuration. The subdomain
 * is one DNS label rather than a URL, so the only address the action can reach is
 * {@code https://{subdomain}.zendesk.com} — there is nothing in it for a request to
 * point somewhere else.
 */
record ZendeskTenant(String origin, String clientId, String sectionId, String userSegmentId) {

    static final String SUBDOMAIN_KEY = "subdomain";
    static final String CLIENT_ID_KEY = "clientId";
    static final String SECTION_KEY = "sectionId";
    static final String USER_SEGMENT_KEY = "userSegmentId";

    private static final int MAX_LABEL_LENGTH = 63;
    private static final int MAX_CLIENT_ID_LENGTH = 255;

    static ZendeskTenant from(Map<String, String> configuration) {
        return new ZendeskTenant(
                "https://" + subdomain(configuration.get(SUBDOMAIN_KEY)) + ".zendesk.com",
                clientId(configuration.get(CLIENT_ID_KEY)),
                identifier(
                        configuration.get(SECTION_KEY),
                        AutomationActionInvalidException.zendeskSectionRequired(),
                        AutomationActionInvalidException.zendeskSectionInvalid()
                ),
                configuration.get(USER_SEGMENT_KEY) == null || configuration.get(USER_SEGMENT_KEY).isBlank()
                        ? null
                        : identifier(
                                configuration.get(USER_SEGMENT_KEY),
                                AutomationActionInvalidException.zendeskUserSegmentInvalid(),
                                AutomationActionInvalidException.zendeskUserSegmentInvalid()
                        )
        );
    }

    private static String subdomain(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.zendeskSubdomainRequired();
        }
        String label = value.strip().toLowerCase(Locale.ROOT);
        if (label.length() > MAX_LABEL_LENGTH || !isLabel(label)) {
            throw AutomationActionInvalidException.zendeskSubdomainInvalid();
        }
        return label;
    }

    private static String clientId(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.zendeskClientIdRequired();
        }
        String clientId = value.strip();
        if (clientId.length() > MAX_CLIENT_ID_LENGTH) {
            throw AutomationActionInvalidException.zendeskClientIdRequired();
        }
        return clientId;
    }

    /** A Zendesk id is a positive number, and one this side can still count with. */
    private static String identifier(
            String value,
            AutomationActionInvalidException missing,
            AutomationActionInvalidException invalid
    ) {
        if (value == null || value.isBlank()) {
            throw missing;
        }
        String identifier = value.strip();
        if (identifier.length() > 19 || identifier.charAt(0) == '0' || !isDigits(identifier)) {
            throw invalid;
        }
        try {
            Long.parseLong(identifier);
        } catch (NumberFormatException tooLarge) {
            throw invalid;
        }
        return identifier;
    }

    // Walked rather than matched, so no pattern is ever asked to back-track over a value
    // somebody else chose.
    private static boolean isLabel(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lower = character >= 'a' && character <= 'z';
            boolean hyphen = character == '-';
            if (!digit && !lower && !hyphen) {
                return false;
            }
            // A label neither starts nor ends with a hyphen.
            if (hyphen && (index == 0 || index == value.length() - 1)) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    private static boolean isDigits(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
