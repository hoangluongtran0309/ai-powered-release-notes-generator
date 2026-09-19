package com.hoangluongtran0309.releaseflow.automation;

/**
 * How one delivery ended. {@code UNKNOWN} means the provider may have accepted it:
 * nothing may repeat such an Action without a person saying so.
 */
record ActionResult(ExecutionStatus outcome, String externalReference, String errorCode) {

    static final String OUTCOME_UNKNOWN = "execution_outcome_unknown";
    static final String NOTE_MISSING = "release_note_missing";
    static final String ACTION_UNSUPPORTED = "action_unsupported";

    static ActionResult succeeded(String externalReference) {
        return new ActionResult(ExecutionStatus.SUCCEEDED, externalReference, null);
    }

    static ActionResult failed(String errorCode) {
        return new ActionResult(ExecutionStatus.FAILED, null, errorCode);
    }

    static ActionResult unknown(String errorCode) {
        return new ActionResult(ExecutionStatus.UNKNOWN, null, errorCode);
    }
}
