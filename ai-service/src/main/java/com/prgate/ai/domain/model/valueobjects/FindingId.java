package com.prgate.ai.domain.model.valueobjects;
import java.util.UUID;
public record FindingId(UUID value) {
 public FindingId { Checks.present(value); }
 public static FindingId create() { return new FindingId(UUID.randomUUID()); }
}