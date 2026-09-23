package com.hoangluongtran0309.releaseflow.release;

import java.util.Locale;
import java.util.UUID;

/**
 * One change in the grouped preview, and in the notes of releases published before
 * audiences existed. {@code category} is the category code; {@code categoryName} is
 * absent from notes stored before categories became a catalog.
 */
public record ReleaseNoteItem(
        UUID changeId,
        int pullRequestNumber,
        String title,
        String url,
        String category,
        boolean breaking,
        String categoryName
) {

    /** The category's name, or its code for notes that did not record one. */
    public String categoryLabel() {
        if (categoryName != null) {
            return categoryName;
        }
        return category == null || category.isEmpty()
                ? ""
                : category.charAt(0) + category.substring(1).toLowerCase(Locale.ROOT);
    }
}
