package com.hoangluongtran0309.releaseflow.account;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The name an Organization goes by in a public URL: one DNS label, such as
 * {@code acme} or {@code acme-tools}. It is a single label rather than free text
 * because the same value has to work both as a path segment and, where a deployment
 * provisions wildcard DNS, as a subdomain.
 */
public record OrganizationSlug(String value) {

    public static final int MAX_LENGTH = 63;

    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern NOT_LABEL_CHARACTER = Pattern.compile("[^a-z0-9]+");
    private static final String FALLBACK = "org";

    public static OrganizationSlug parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidOrganizationSlugException("error.organization_slug_invalid.required");
        }
        String candidate = value.strip().toLowerCase(Locale.ROOT);
        if (candidate.length() > MAX_LENGTH || !LABEL.matcher(candidate).matches()) {
            throw new InvalidOrganizationSlugException(
                    "error.organization_slug_invalid.format", value.strip(), MAX_LENGTH
            );
        }
        return new OrganizationSlug(candidate);
    }

    /**
     * A slug made from an Organization's name, for the moment it is registered. It is a
     * suggestion, not an identity: whoever takes the name first keeps the plain slug and
     * the next one is numbered.
     */
    static OrganizationSlug fromName(String name) {
        String candidate = NOT_LABEL_CHARACTER
                .matcher(name == null ? "" : name.toLowerCase(Locale.ROOT))
                .replaceAll("-");
        candidate = trimHyphens(candidate);
        if (candidate.isBlank()) {
            candidate = FALLBACK;
        }
        return new OrganizationSlug(trimHyphens(shorten(candidate, MAX_LENGTH)));
    }

    /** The same slug with a number after it, for a name somebody else already took. */
    OrganizationSlug numbered(int attempt) {
        String suffix = "-" + attempt;
        return new OrganizationSlug(trimHyphens(shorten(value, MAX_LENGTH - suffix.length())) + suffix);
    }

    private static String shorten(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static String trimHyphens(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
