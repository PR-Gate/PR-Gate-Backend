package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record ConfigurationId(UUID value) {
    public ConfigurationId { Checks.required(value); }
    public static ConfigurationId create() { return new ConfigurationId(UUID.randomUUID()); }
}
