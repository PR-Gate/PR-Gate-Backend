package com.prgate.ai.domain.model.valueobjects;
import java.util.UUID;
public record PlanId(UUID value) {
 public PlanId { Checks.present(value); }
 public static PlanId create() { return new PlanId(UUID.randomUUID()); }
}