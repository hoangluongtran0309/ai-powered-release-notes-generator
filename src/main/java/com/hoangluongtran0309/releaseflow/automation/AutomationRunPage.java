package com.hoangluongtran0309.releaseflow.automation;

import java.util.List;

/**
 * One page of the run history, newest first. The shape is ReleaseFlow's own rather
 * than a framework's, so the JSON stays stable.
 */
public record AutomationRunPage(List<AutomationRunView> items, int page, int size, long total) {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    public AutomationRunPage {
        items = List.copyOf(items);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < total;
    }

    /** Checks a requested page, because nobody can be shown one that cannot exist. */
    static void requireValid(int page, int size) {
        if (page < 0) {
            throw new InvalidAutomationRunPageException("error.invalid_automation_run_page.negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidAutomationRunPageException("error.invalid_automation_run_page.size", MAX_SIZE);
        }
    }
}
