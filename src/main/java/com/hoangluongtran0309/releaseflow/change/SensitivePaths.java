package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.github.ChangedFile;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compiled glob patterns that force human review when a changed file matches one. */
final class SensitivePaths {

    private final List<CompiledRule> rules;

    private SensitivePaths(List<CompiledRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * @throws IllegalArgumentException naming the first pattern that is not a valid glob
     */
    static SensitivePaths compile(Collection<String> globs) {
        List<CompiledRule> compiled = new ArrayList<>();
        for (String glob : globs) {
            compiled.add(CompiledRule.compile(glob));
        }
        return new SensitivePaths(compiled);
    }

    SensitivePaths plus(SensitivePaths other) {
        List<CompiledRule> combined = new ArrayList<>(rules);
        combined.addAll(other.rules);
        return new SensitivePaths(combined);
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
                throw new IllegalArgumentException(glob, exception);
            }
        }

        boolean matches(Path candidate) {
            return matcher.matches(candidate) || (unanchoredMatcher != null && unanchoredMatcher.matches(candidate));
        }
    }
}
