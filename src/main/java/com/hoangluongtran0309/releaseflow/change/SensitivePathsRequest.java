package com.hoangluongtran0309.releaseflow.change;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public class SensitivePathsRequest {

    @NotNull(message = "List the patterns to add, or none.")
    private List<String> additions;

    public List<String> getAdditions() {
        return additions;
    }

    public void setAdditions(List<String> additions) {
        this.additions = additions;
    }
}
