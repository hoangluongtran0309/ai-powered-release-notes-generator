package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;

/** Two Organizations cannot answer the same public address. */
public class OrganizationSlugTakenException extends LocalizedException {

    public OrganizationSlugTakenException(String slug) {
        super("error.organization_slug_taken", slug);
    }
}
