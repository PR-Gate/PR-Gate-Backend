package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record EvaluationId(UUID value) {
    public EvaluationId { Checks.required(value); }
    public static EvaluationId create() { return new EvaluationId(UUID.randomUUID()); }
}
