package com.prgate.projects.application.result;

import java.time.Instant;
import java.util.UUID;

public record CreateProjectResult(UUID id, UUID courseId, String name, String description, Instant createdAt) {
}
