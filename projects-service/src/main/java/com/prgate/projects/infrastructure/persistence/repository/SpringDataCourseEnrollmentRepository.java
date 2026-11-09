package com.prgate.projects.infrastructure.persistence.repository;

import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.infrastructure.persistence.entity.CourseEnrollmentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataCourseEnrollmentRepository extends JpaRepository<CourseEnrollmentJpaEntity, UUID> {

    boolean existsByCourseIdAndStudentIdAndStatusIn(UUID courseId, UUID studentId, Collection<EnrollmentStatus> statuses);

    Optional<CourseEnrollmentJpaEntity> findByCourseIdAndStudentIdAndStatus(UUID courseId, UUID studentId, EnrollmentStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO course_enrollments (id, course_id, student_id, status, requested_at, resolved_at)
            VALUES (:id, :courseId, :studentId, 'PENDING', :requestedAt, NULL)
            """, nativeQuery = true)
    int insertPending(@Param("id") UUID id, @Param("courseId") UUID courseId,
                      @Param("studentId") UUID studentId, @Param("requestedAt") Instant requestedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update CourseEnrollmentJpaEntity e
            set e.status = :decision, e.resolvedAt = :resolvedAt
            where e.id = :id and e.courseId = :courseId and e.studentId = :studentId
              and e.requestedAt = :requestedAt and e.status = :pending
            """)
    int resolvePending(@Param("id") UUID id, @Param("courseId") UUID courseId, @Param("studentId") UUID studentId,
                       @Param("requestedAt") Instant requestedAt, @Param("decision") EnrollmentStatus decision,
                       @Param("resolvedAt") Instant resolvedAt, @Param("pending") EnrollmentStatus pending);
}
