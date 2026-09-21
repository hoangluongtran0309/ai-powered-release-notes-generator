package com.hoangluongtran0309.releaseflow.automation;

import java.util.Locale;
import java.util.UUID;

/**
 * The Notion page a release note is filed under, read from an Action's configuration.
 * Notion writes a page id both as a bare 32-character hexadecimal string and as a UUID,
 * so a person may paste either; both are stored and sent as the UUID form.
 */
final class NotionParentPage {

    static final String KEY = "parentPageId";
    private static final int ID_LENGTH = 32;

    private NotionParentPage() {
    }

    static String normalized(String value) {
        if (value == null || value.isBlank()) {
            throw AutomationActionInvalidException.notionParentRequired();
        }
        String compact = value.strip().replace("-", "");
        if (compact.length() != ID_LENGTH || !isHexadecimal(compact)) {
            throw AutomationActionInvalidException.notionParentInvalid();
        }
        String lower = compact.toLowerCase(Locale.ROOT);
        String dashed = lower.substring(0, 8) + "-"
                + lower.substring(8, 12) + "-"
                + lower.substring(12, 16) + "-"
                + lower.substring(16, 20) + "-"
                + lower.substring(20);
        return UUID.fromString(dashed).toString();
    }

    // Walked rather than matched, so no pattern is ever asked to back-track over a
    // string somebody else chose.
    private static boolean isHexadecimal(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean digit = character >= '0' && character <= '9';
            boolean lower = character >= 'a' && character <= 'f';
            boolean upper = character >= 'A' && character <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }
}
