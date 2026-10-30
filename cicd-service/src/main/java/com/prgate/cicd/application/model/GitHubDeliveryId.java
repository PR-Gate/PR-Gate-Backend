package com.prgate.cicd.application.model;

import java.util.Objects;
import java.util.UUID;

/** Unique identifier supplied by GitHub for one webhook delivery. */
public record GitHubDeliveryId(UUID value) {

    public GitHubDeliveryId {
        Objects.requireNonNull(value, "GitHub delivery id is required");
    }

    public static GitHubDeliveryId from(String value) {
        Objects.requireNonNull(value, "GitHub delivery id is required");
        try {
            return new GitHubDeliveryId(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("GitHub delivery id must be a valid UUID", exception);
        }
    }
}
