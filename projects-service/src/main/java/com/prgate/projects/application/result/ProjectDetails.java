package com.prgate.projects.application.result;

import java.time.Instant;
import java.util.UUID;

public record ProjectDetails(UUID id, UUID courseId, String name, String description, UUID leaderId,
                             Long organizationId, String organizationLogin, Instant createdAt) { }
