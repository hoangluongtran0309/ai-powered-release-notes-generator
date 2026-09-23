package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;

import java.util.List;

/**
 * What an issue tracker could add about a change. The issues are evidence: they never
 * settle a change, and an incomplete lookup asks for a person instead of being ignored.
 *
 * @param retryable whether asking again later may complete the lookup
 */
record LinkedContext(LinkedContextStatus status, List<LinkedIssue> issues, boolean retryable) {

    static final LinkedContext NOT_SUPPORTED =
            new LinkedContext(LinkedContextStatus.NOT_SUPPORTED, List.of(), false);
    static final LinkedContext NOT_CONFIGURED =
            new LinkedContext(LinkedContextStatus.NOT_CONFIGURED, List.of(), false);

    LinkedContext {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    static LinkedContext of(LinkedContextStatus status, List<LinkedIssue> issues) {
        return new LinkedContext(status, issues, false);
    }

    static LinkedContext retryable(LinkedContextStatus status, List<LinkedIssue> issues) {
        return new LinkedContext(status, issues, true);
    }
}
