package com.hoangluongtran0309.releaseflow.release;

import java.util.List;

public record ReleaseNoteSection(String title, List<ReleaseNoteItem> items) {

    public ReleaseNoteSection {
        items = List.copyOf(items);
    }
}
