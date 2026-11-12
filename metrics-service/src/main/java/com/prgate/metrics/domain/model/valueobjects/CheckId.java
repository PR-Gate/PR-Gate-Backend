package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record CheckId(UUID value) {
    public CheckId { Checks.required(value); }
    public static CheckId create() { return new CheckId(UUID.randomUUID()); }
}
