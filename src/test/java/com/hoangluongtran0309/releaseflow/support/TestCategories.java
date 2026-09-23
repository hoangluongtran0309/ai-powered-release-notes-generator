package com.hoangluongtran0309.releaseflow.support;

import com.hoangluongtran0309.releaseflow.category.CategoryGroup;
import com.hoangluongtran0309.releaseflow.category.CategoryRef;

import java.util.List;

/** The categories every Organization starts with, for tests that classify changes. */
public final class TestCategories {

    public static final CategoryRef FEATURE = new CategoryRef("FEATURE", "Feature", CategoryGroup.FEATURE);
    public static final CategoryRef FIX = new CategoryRef("FIX", "Fix", CategoryGroup.FIX);
    public static final CategoryRef PERFORMANCE = new CategoryRef("PERFORMANCE", "Performance", CategoryGroup.PERFORMANCE);
    public static final CategoryRef DOCUMENTATION =
            new CategoryRef("DOCUMENTATION", "Documentation", CategoryGroup.DOCUMENTATION);
    public static final CategoryRef MAINTENANCE = new CategoryRef("MAINTENANCE", "Maintenance", CategoryGroup.MAINTENANCE);
    public static final CategoryRef UNKNOWN = CategoryRef.UNKNOWN;

    /** The seeded catalog, ordered by code as the service returns it. */
    public static final List<CategoryRef> CATALOG = List.of(DOCUMENTATION, FEATURE, FIX, MAINTENANCE, PERFORMANCE, UNKNOWN);

    private TestCategories() {
    }
}
