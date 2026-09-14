package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.ChangedFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * File paths that force human review whatever else the classification concluded.
 * Patterns use JDK glob syntax. An invalid pattern or an empty list stops the
 * application from starting, so a typo can never silently disable the rule.
 */
@Component
final class SensitivePathRules {

    private final List<CompiledRule> rules;

    SensitivePathRules(@Value("${releaseflow.classification.sensitive-paths}") List<String> globs) {
        List<CompiledRule> compiled = new ArrayList<>();
        for (String glob : globs) {
            String trimmed = glob == null ? "" : glob.strip();
            if (!trimmed.isEmpty()) {
                compiled.add(CompiledRule.compile(trimmed));
            }
        }
        if (compiled.isEmpty()) {
            throw new IllegalStateException(
                    "releaseflow.classification.sensitive-paths must contain at least one pattern."
            );
        }
        this.rules = List.copyOf(compiled);
    }

    /**
     * Every changed path that matched a rule, in reported order and without duplicates.
     * A rename is checked on both paths: moving a file out of a sensitive directory is
     * itself a sensitive change.
     */
    List<String> matches(Collection<ChangedFile> files) {
        Set<String> matched = new LinkedHashSet<>();
        for (ChangedFile file : files) {
            if (matchesAnyRule(file.path())) {
                matched.add(file.path());
            } else if (file.previousPath() != null && matchesAnyRule(file.previousPath())) {
                matched.add(file.previousPath());
            }
        }
        return List.copyOf(matched);
    }

    private boolean matchesAnyRule(String path) {
        final Path candidate;
        try {
            candidate = Path.of(path);
        } catch (InvalidPathException exception) {
            // A path that cannot be parsed is not a reason to declare the change safe.
            return true;
        }
        return rules.stream().anyMatch(rule -> rule.matches(candidate));
    }

    /**
     * The JDK requires {@code **} to match at least one directory, so {@code **}{@code /x}
     * would miss a top-level {@code x}. The stripped pattern is compiled alongside it so
     * the rule means "anywhere", as its author intended.
     */
    private record CompiledRule(PathMatcher matcher, PathMatcher unanchoredMatcher) {

        static CompiledRule compile(String glob) {
            try {
                PathMatcher unanchored = glob.startsWith("**/")
                        ? FileSystems.getDefault().getPathMatcher("glob:" + glob.substring(3))
                        : null;
                return new CompiledRule(FileSystems.getDefault().getPathMatcher("glob:" + glob), unanchored);
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "releaseflow.classification.sensitive-paths contains an invalid pattern: " + glob,
                        exception
                );
            }
        }

        boolean matches(Path candidate) {
            return matcher.matches(candidate) || (unanchoredMatcher != null && unanchoredMatcher.matches(candidate));
        }
    }
}
