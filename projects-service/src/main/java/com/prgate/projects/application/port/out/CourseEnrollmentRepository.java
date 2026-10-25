package com.prgate.projects.application.port.out;

import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.util.Optional;

public interface CourseEnrollmentRepository {

    Optional<CourseEnrollment> findById(CourseEnrollmentId enrollmentId);

    /** Returns only APPROVED admission for this course and student. */
    Optional<CourseEnrollment> findApproved(CourseId courseId, StudentId studentId);

    /** Rejected history does not block a new request. */
    boolean existsPendingOrApproved(CourseId courseId, StudentId studentId);

    /**
     * Inserts a new PENDING request. The adapter must enforce uniqueness of
     * PENDING/APPROVED per course and student, even under concurrent requests,
     * and report conflicts with EnrollmentConflictException.
     */
    void add(CourseEnrollment enrollment);

    /**
     * Atomically persists the decision only if the stored request is still
     * PENDING. A stale or missing request must raise EnrollmentConflictException.
     */
    void resolve(CourseEnrollment enrollment);
}
