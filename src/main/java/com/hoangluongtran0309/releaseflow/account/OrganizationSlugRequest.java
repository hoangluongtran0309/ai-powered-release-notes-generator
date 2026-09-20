package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class OrganizationSlugRequest {

    @NotBlank(message = "A changelog address is required.")
    @Size(max = OrganizationSlug.MAX_LENGTH, message = "The changelog address is too long.")
    private String slug;

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug == null ? null : slug.strip();
    }
}
