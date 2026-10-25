package com.prgate.projects.domain.model.aggregates;

import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.time.Instant;
import java.util.Optional;

/**
 * Aggregate root for a student's admission request to a course.
 * Admission is independent of participation in a project.
 */
public final class CourseEnrollment {

    private final CourseEnrollmentId id;
    private final CourseId courseId;
    private final StudentId studentId;
    private final Instant requestedAt;
    private EnrollmentStatus status;
    private Instant resolvedAt;

    private CourseEnrollment(CourseEnrollmentId id, CourseId courseId, StudentId studentId, Instant requestedAt) {
        this.id = requireValue(id, "Course enrollment id is required");
        this.courseId = requireValue(courseId, "Course id is required");
        this.studentId = requireValue(studentId, "Student id is required");
        this.requestedAt = requireValue(requestedAt, "Request time is required");
        this.status = EnrollmentStatus.PENDING;
    }

    public static CourseEnrollment request(CourseEnrollmentId id, CourseId courseId,
                                           StudentId studentId, Instant requestedAt) {
        return new CourseEnrollment(id, courseId, studentId, requestedAt);
    }

    public void approve(Instant resolvedAt) {
        resolve(EnrollmentStatus.APPROVED, resolvedAt);
    }

    public void reject(Instant resolvedAt) {
        resolve(EnrollmentStatus.REJECTED, resolvedAt);
    }

    public CourseEnrollmentId getId() {
        return id;
    }

    public CourseId getCourseId() {
        return courseId;
    }

    public StudentId getStudentId() {
        return studentId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    private void resolve(EnrollmentStatus decision, Instant decisionTime) {
        if (status != EnrollmentStatus.PENDING) {
            throw new IllegalStateException("Only pending enrollment requests can be resolved");
        }
        requireValue(decisionTime, "Resolution time is required");
        if (decisionTime.isBefore(requestedAt)) {
            throw new IllegalArgumentException("Resolution time must not precede request time");
        }
        this.status = decision;
        this.resolvedAt = decisionTime;
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
