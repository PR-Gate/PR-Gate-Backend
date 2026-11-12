package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record RubricId(UUID value) {
    public RubricId { Checks.required(value); }
    public static RubricId create() { return new RubricId(UUID.randomUUID()); }
}
