package com.prgate.projects.infrastructure.persistence.repository;

import com.prgate.projects.infrastructure.persistence.entity.ProjectMembershipJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataProjectMembershipRepository extends JpaRepository<ProjectMembershipJpaEntity, UUID> {
    Optional<ProjectMembershipJpaEntity> findByCourseIdAndStudentIdAndLeftAtIsNull(UUID courseId, UUID studentId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO project_memberships (id, course_id, project_id, student_id, joined_at)
            VALUES (:id, :courseId, :projectId, :studentId, :joinedAt)
            """, nativeQuery = true)
    int insertActive(@Param("id") UUID id, @Param("courseId") UUID courseId,
                     @Param("projectId") UUID projectId, @Param("studentId") UUID studentId,
                     @Param("joinedAt") Instant joinedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE project_memberships SET left_at = :leftAt
            WHERE id = :id AND course_id = :courseId AND project_id = :projectId
              AND student_id = :studentId AND joined_at = :joinedAt AND left_at IS NULL
            """, nativeQuery = true)
    int closeActive(@Param("id") UUID id, @Param("courseId") UUID courseId,
                    @Param("projectId") UUID projectId, @Param("studentId") UUID studentId,
                    @Param("joinedAt") Instant joinedAt, @Param("leftAt") Instant leftAt);
}
