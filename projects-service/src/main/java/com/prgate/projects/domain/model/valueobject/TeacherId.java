package com.prgate.projects.domain.model.valueobject;

import java.util.UUID;

/** Reference to a local user in the Users service, not a Cognito subject. */
public record TeacherId(UUID value) {

    public TeacherId {
        if (value == null) {
            throw new IllegalArgumentException("Teacher id is required");
        }
    }
}
