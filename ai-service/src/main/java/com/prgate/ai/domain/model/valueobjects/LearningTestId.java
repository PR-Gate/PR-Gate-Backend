package com.prgate.ai.domain.model.valueobjects;
import java.util.UUID;
public record LearningTestId(UUID value) {
 public LearningTestId { Checks.present(value); }
 public static LearningTestId create() { return new LearningTestId(UUID.randomUUID()); }
}