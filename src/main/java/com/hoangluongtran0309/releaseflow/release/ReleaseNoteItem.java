package com.hoangluongtran0309.releaseflow.release;

import com.hoangluongtran0309.releaseflow.change.ChangeCategory;

import java.util.UUID;

public record ReleaseNoteItem(
        UUID changeId,
        int pullRequestNumber,
        String title,
        String url,
        ChangeCategory category,
        boolean breaking
) {
}
