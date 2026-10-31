package com.prgate.projects.application.model;

import java.util.Objects;
import java.util.UUID;

/** Minimal local projection of Users; not a persisted profile or a domain entity. */
public record UserAccount(UUID id, Role role, Status status) {

    public UserAccount {
        Objects.requireNonNull(id, "User id is required");
        Objects.requireNonNull(role, "User role is required");
        Objects.requireNonNull(status, "User status is required");
    }

    public enum Role {
        STUDENT,
        TEACHER,
        ADMIN
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
