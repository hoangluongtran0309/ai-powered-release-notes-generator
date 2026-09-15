package com.hoangluongtran0309.releaseflow.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The editable details of a category. */
public class CategoryRequest {

    @NotBlank(message = "Display name is required.")
    @Size(max = 120, message = "Display name must not exceed 120 characters.")
    private String displayName;

    @NotNull(message = "Choose a group.")
    private CategoryGroup group;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.strip();
    }

    public CategoryGroup getGroup() {
        return group;
    }

    public void setGroup(CategoryGroup group) {
        this.group = group;
    }
}
