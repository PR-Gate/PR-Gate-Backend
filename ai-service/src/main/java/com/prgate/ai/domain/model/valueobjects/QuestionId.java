package com.prgate.ai.domain.model.valueobjects;
import java.util.UUID;
public record QuestionId(UUID value) {
 public QuestionId { Checks.present(value); }
 public static QuestionId create() { return new QuestionId(UUID.randomUUID()); }
}