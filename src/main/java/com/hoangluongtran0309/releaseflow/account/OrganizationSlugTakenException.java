package com.hoangluongtran0309.releaseflow.account;

/** Two Organizations cannot answer the same public address. */
public class OrganizationSlugTakenException extends RuntimeException {

    public OrganizationSlugTakenException(String slug) {
        super("The changelog address \"" + slug + "\" is already taken.");
    }
}
