package com.prgate.projects.application.result;

import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record CourseEnrollmentResult(UUID id, UUID courseId, UUID studentId, EnrollmentStatus status,
                                     Instant requestedAt, Optional<Instant> resolvedAt) {

    public static CourseEnrollmentResult from(CourseEnrollment enrollment) {
        return new CourseEnrollmentResult(enrollment.getId().value(), enrollment.getCourseId().value(),
                enrollment.getStudentId().value(), enrollment.getStatus(), enrollment.getRequestedAt(),
                enrollment.getResolvedAt());
    }
}
