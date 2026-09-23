package com.hoangluongtran0309.releaseflow.automation;

/**
 * Asks for the first failed action of a run to be carried out again. An action whose
 * outcome is unknown may already have been delivered, so repeating it takes saying so.
 */
public class RetryRunRequest {

    private boolean confirmUnknown;

    public boolean isConfirmUnknown() {
        return confirmUnknown;
    }

    public void setConfirmUnknown(boolean confirmUnknown) {
        this.confirmUnknown = confirmUnknown;
    }
}
