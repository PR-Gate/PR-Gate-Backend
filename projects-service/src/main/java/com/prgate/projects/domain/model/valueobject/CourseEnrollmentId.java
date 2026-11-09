package com.prgate.projects.domain.model.valueobject;

import java.util.UUID;

public record CourseEnrollmentId(UUID value) {

    public CourseEnrollmentId {
        if (value == null) {
            throw new IllegalArgumentException("Course enrollment id is required");
        }
    }
}
