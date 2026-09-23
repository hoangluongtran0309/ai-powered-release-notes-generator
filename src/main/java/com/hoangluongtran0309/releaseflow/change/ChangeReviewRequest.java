package com.hoangluongtran0309.releaseflow.change;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChangeReviewRequest {

    @NotBlank(message = "{validation.category.required}")
    private String category;

    @NotNull(message = "{validation.breaking.required}")
    private Boolean breaking;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? null : category.strip();
    }

    public Boolean getBreaking() {
        return breaking;
    }

    public void setBreaking(Boolean breaking) {
        this.breaking = breaking;
    }
}
