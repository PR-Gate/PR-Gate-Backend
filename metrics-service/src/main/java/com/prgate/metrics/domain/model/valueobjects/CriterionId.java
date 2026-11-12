package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record CriterionId(UUID value) {
    public CriterionId { Checks.required(value); }
    public static CriterionId create() { return new CriterionId(UUID.randomUUID()); }
}
