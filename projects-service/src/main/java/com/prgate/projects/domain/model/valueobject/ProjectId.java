package com.prgate.projects.domain.model.valueobject;

import java.util.UUID;

public record ProjectId(UUID value) {

    public ProjectId {
        if (value == null) {
            throw new IllegalArgumentException("Project id is required");
        }
    }
}
