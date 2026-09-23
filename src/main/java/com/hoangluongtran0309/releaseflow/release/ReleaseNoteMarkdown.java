package com.hoangluongtran0309.releaseflow.release;

/**
 * Markdown helpers for release notes. Untrusted text such as pull request titles is
 * escaped so it cannot add links, emphasis, or HTML to a note.
 */
final class ReleaseNoteMarkdown {

    private ReleaseNoteMarkdown() {
    }

    static String escape(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if ("\\`*_[]<>".indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
