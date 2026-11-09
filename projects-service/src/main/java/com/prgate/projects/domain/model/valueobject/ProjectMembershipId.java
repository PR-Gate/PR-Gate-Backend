package com.prgate.projects.domain.model.valueobject;

import java.util.UUID;

public record ProjectMembershipId(UUID value) {

    public ProjectMembershipId {
        if (value == null) {
            throw new IllegalArgumentException("Project membership id is required");
        }
    }
}
