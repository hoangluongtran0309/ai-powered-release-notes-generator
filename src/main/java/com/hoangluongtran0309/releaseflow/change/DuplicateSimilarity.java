package com.hoangluongtran0309.releaseflow.change;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * An explainable similarity between two changes, from 0 to 1: character trigrams of
 * their titles and of their content (description and what changed), and, when both
 * list changed files, the overlap of those paths.
 */
final class DuplicateSimilarity {

    private DuplicateSimilarity() {
    }

    record Subject(String title, String content, Set<String> paths) {

        Subject {
            paths = Set.copyOf(paths);
        }
    }

    record Match(double score, double title, double content, double paths) {
    }

    static Match compare(Subject left, Subject right) {
        double title = jaccard(trigrams(left.title()), trigrams(right.title()));
        double content = jaccard(trigrams(left.content()), trigrams(right.content()));
        double paths = jaccard(left.paths(), right.paths());
        double score = left.paths().isEmpty() || right.paths().isEmpty()
                ? title * 0.45 + content * 0.55
                : title * 0.40 + content * 0.40 + paths * 0.20;
        return new Match(score, title, content, paths);
    }

    // NFKC, lower case, and every run of characters other than letters and digits as one space.
    static Set<String> trigrams(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
        Set<String> trigrams = new HashSet<>();
        if (normalized.length() < 3) {
            if (!normalized.isEmpty()) {
                trigrams.add(normalized);
            }
            return trigrams;
        }
        for (int index = 0; index <= normalized.length() - 3; index++) {
            trigrams.add(normalized.substring(index, index + 3));
        }
        return trigrams;
    }

    static double jaccard(Collection<String> left, Collection<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }
}
