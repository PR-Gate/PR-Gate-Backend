package com.prgate.ai.domain.model.valueobjects;
import java.util.UUID;
public record CriterionId(UUID value) {
 public CriterionId { Checks.present(value); }
 public static CriterionId create() { return new CriterionId(UUID.randomUUID()); }
}