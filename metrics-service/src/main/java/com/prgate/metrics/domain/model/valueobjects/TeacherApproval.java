package com.prgate.metrics.domain.model.valueobjects;
import java.time.Instant;
import java.util.UUID;
/** Authorization of the teacher is checked by the application boundary. */
public record TeacherApproval(UUID teacherId, Instant approvedAt) {
    public TeacherApproval { Checks.required(teacherId); Checks.required(approvedAt); }
}
