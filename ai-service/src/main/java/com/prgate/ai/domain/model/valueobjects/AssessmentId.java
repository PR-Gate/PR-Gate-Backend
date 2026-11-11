package com.prgate.ai.domain.model.valueobjects;
import java.util.UUID;
public record AssessmentId(UUID value) {
 public AssessmentId { Checks.present(value); }
 public static AssessmentId create() { return new AssessmentId(UUID.randomUUID()); }
}