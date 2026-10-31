package com.prgate.projects.application.security;

import java.util.Objects;
import java.util.UUID;

/** Local user identity resolved by the authentication adapter, never from request data. */
public record Actor(UUID userId) {

    public Actor {
        Objects.requireNonNull(userId, "Actor user id is required");
    }
}
