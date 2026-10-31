package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.exception.EnrollmentConflictException;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.infrastructure.persistence.mapper.CourseEnrollmentPersistenceMapper;
import com.prgate.projects.infrastructure.persistence.repository.SpringDataCourseEnrollmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaCourseEnrollmentRepositoryAdapter implements CourseEnrollmentRepository {

    private final SpringDataCourseEnrollmentRepository repository;

    public JpaCourseEnrollmentRepositoryAdapter(SpringDataCourseEnrollmentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseEnrollment> findById(CourseEnrollmentId enrollmentId) {
        return repository.findById(enrollmentId.value()).map(CourseEnrollmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseEnrollment> findApproved(CourseId courseId, StudentId studentId) {
        return repository.findByCourseIdAndStudentIdAndStatus(courseId.value(), studentId.value(), EnrollmentStatus.APPROVED)
                .map(CourseEnrollmentPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsPendingOrApproved(CourseId courseId, StudentId studentId) {
        return repository.existsByCourseIdAndStudentIdAndStatusIn(courseId.value(), studentId.value(),
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.APPROVED));
    }

    @Override
    @Transactional
    public void add(CourseEnrollment enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new IllegalArgumentException("Only new pending enrollment requests can be added");
        }
        try {
            // Explicit INSERT never merges or overwrites a historical request.
            repository.insertPending(enrollment.getId().value(), enrollment.getCourseId().value(),
                    enrollment.getStudentId().value(), enrollment.getRequestedAt());
        } catch (DataIntegrityViolationException failure) {
            if (isUniqueViolation(failure)) {
                throw new EnrollmentConflictException("Enrollment already exists or another pending/approved request was found", failure);
            }
            throw failure;
        }
    }

    @Override
    @Transactional
    public void resolve(CourseEnrollment enrollment) {
        if (enrollment.getStatus() == EnrollmentStatus.PENDING) {
            throw new IllegalArgumentException("A final enrollment decision is required");
        }
        int changed = repository.resolvePending(enrollment.getId().value(), enrollment.getCourseId().value(),
                enrollment.getStudentId().value(), enrollment.getRequestedAt(), enrollment.getStatus(),
                enrollment.getResolvedAt().orElseThrow(), EnrollmentStatus.PENDING);
        if (changed != 1) {
            throw new EnrollmentConflictException("Enrollment is missing, changed or no longer pending");
        }
    }

    private static boolean isUniqueViolation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
