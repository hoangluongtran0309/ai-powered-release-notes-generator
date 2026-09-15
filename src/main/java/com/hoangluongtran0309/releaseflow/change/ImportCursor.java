package com.hoangluongtran0309.releaseflow.change;

/**
 * Where an import stands in the source's list: the page, counted from one, and how many
 * pull requests of that page were already handled. Stored as {@code page:offset}.
 */
record ImportCursor(int page, int offset) {

    static final ImportCursor START = new ImportCursor(1, 0);

    ImportCursor {
        if (page < 1 || offset < 0) {
            throw new IllegalArgumentException("Invalid import cursor " + page + ":" + offset);
        }
    }

    static ImportCursor parse(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid import cursor " + value);
        }
        return new ImportCursor(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    ImportCursor nextPage() {
        return new ImportCursor(page + 1, 0);
    }

    ImportCursor at(int offset) {
        return new ImportCursor(page, offset);
    }

    @Override
    public String toString() {
        return page + ":" + offset;
    }
}
