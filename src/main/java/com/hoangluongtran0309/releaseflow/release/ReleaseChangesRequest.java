package com.hoangluongtran0309.releaseflow.release;

import jakarta.validation.constraints.AssertTrue;

import java.util.List;
import java.util.UUID;

public class ReleaseChangesRequest {

    private List<UUID> changeIds;

    private boolean allAvailable;

    public List<UUID> getChangeIds() {
        return changeIds;
    }

    public void setChangeIds(List<UUID> changeIds) {
        this.changeIds = changeIds;
    }

    public boolean isAllAvailable() {
        return allAvailable;
    }

    public void setAllAvailable(boolean allAvailable) {
        this.allAvailable = allAvailable;
    }

    @AssertTrue(message = "Choose changes to add, or add all available changes.")
    public boolean isSelection() {
        boolean hasIds = changeIds != null && !changeIds.isEmpty();
        return hasIds != allAvailable;
    }
}
