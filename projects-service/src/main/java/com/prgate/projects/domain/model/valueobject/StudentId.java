package com.prgate.projects.domain.model.valueobject;

import java.util.UUID;

/** Reference to a local user in the Users service, not a Cognito subject. */
public record StudentId(UUID value) {

    public StudentId {
        if (value == null) {
            throw new IllegalArgumentException("Student id is required");
        }
    }
}
