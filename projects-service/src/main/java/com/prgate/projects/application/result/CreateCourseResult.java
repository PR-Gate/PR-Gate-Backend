package com.prgate.projects.application.result;

import java.time.Instant;
import java.util.UUID;

public record CreateCourseResult(UUID id, String name, String section, String academicPeriod,
                                 UUID teacherId, Instant createdAt) {
}
