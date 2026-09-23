package com.hoangluongtran0309.releaseflow.change;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The deployment's baseline of sensitive paths. Patterns use JDK glob syntax. An invalid
 * pattern or an empty list stops the application from starting, so a typo can never
 * silently disable the rule. A Project may add patterns but never remove these.
 */
@Component
final class SensitivePathRules {

    private final List<String> baseline;
    private final SensitivePaths compiled;

    SensitivePathRules(@Value("${releaseflow.classification.sensitive-paths}") List<String> globs) {
        Set<String> patterns = new LinkedHashSet<>();
        for (String glob : globs) {
            String trimmed = glob == null ? "" : glob.strip();
            if (!trimmed.isEmpty()) {
                patterns.add(trimmed);
            }
        }
        if (patterns.isEmpty()) {
            throw new IllegalStateException(
                    "releaseflow.classification.sensitive-paths must contain at least one pattern."
            );
        }
        try {
            this.compiled = SensitivePaths.compile(patterns);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "releaseflow.classification.sensitive-paths contains an invalid pattern: " + exception.getMessage(),
                    exception
            );
        }
        this.baseline = List.copyOf(patterns);
    }

    /** The baseline patterns, in configured order. */
    List<String> baseline() {
        return baseline;
    }

    /** The baseline followed by a Project's additions, without duplicates. */
    List<String> effective(List<String> additions) {
        Set<String> patterns = new LinkedHashSet<>(baseline);
        patterns.addAll(additions);
        return List.copyOf(patterns);
    }

    /** The rules a Project's changes are checked against. Additions must already be valid. */
    SensitivePaths forProject(List<String> additions) {
        if (additions.isEmpty()) {
            return compiled;
        }
        List<String> extra = new ArrayList<>(additions);
        extra.removeAll(baseline);
        return extra.isEmpty() ? compiled : compiled.plus(SensitivePaths.compile(extra));
    }
}
