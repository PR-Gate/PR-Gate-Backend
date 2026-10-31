package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record EvaluationRepositoryId(UUID value) {
    public EvaluationRepositoryId { Objects.requireNonNull(value, "Evaluation repository id is required"); }
    public static EvaluationRepositoryId generate() { return new EvaluationRepositoryId(UUID.randomUUID()); }
}
