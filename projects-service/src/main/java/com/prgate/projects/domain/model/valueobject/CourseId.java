package com.prgate.projects.domain.model.valueobject;

import java.util.UUID;

public record CourseId(UUID value) {

    public CourseId {
        if (value == null) {
            throw new IllegalArgumentException("Course id is required");
        }
    }
}
