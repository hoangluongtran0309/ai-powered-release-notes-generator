package com.hoangluongtran0309.releaseflow.change;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the issue keys of one Jira project in whatever a change says about itself. Only
 * the connected project's keys count, and only a handful of them: a change that names a
 * hundred issues is not better evidence than one that names three.
 */
final class JiraKeys {

    static final int MAX_KEYS = 10;
    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]*");

    private JiraKeys() {
    }

    static List<String> extract(String projectKey, List<String> evidence) {
        String project = projectKey == null ? "" : projectKey.strip().toUpperCase(Locale.ROOT);
        if (!PROJECT_KEY.matcher(project).matches()) {
            return List.of();
        }
        // The guards keep a key from matching inside a longer token, such as XAPP-1 or APP-1a.
        Pattern pattern = Pattern.compile(
                "(?<![A-Z0-9_])(" + Pattern.quote(project) + "-[1-9][0-9]*)(?![A-Z0-9_-])",
                Pattern.CASE_INSENSITIVE
        );
        Set<String> keys = new LinkedHashSet<>();
        for (String value : evidence) {
            if (value == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(value);
            while (matcher.find() && keys.size() < MAX_KEYS) {
                keys.add(matcher.group(1).toUpperCase(Locale.ROOT));
            }
            if (keys.size() == MAX_KEYS) {
                break;
            }
        }
        return List.copyOf(keys);
    }
}
