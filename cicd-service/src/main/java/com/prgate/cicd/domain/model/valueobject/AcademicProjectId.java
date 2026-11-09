package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

/** External reference owned by the Projects microservice. */
public record AcademicProjectId(UUID value) {
    public AcademicProjectId { Objects.requireNonNull(value, "Academic project id is required"); }
}
