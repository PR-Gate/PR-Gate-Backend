package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.infrastructure.persistence.mapper.ProjectMembershipPersistenceMapper;
import com.prgate.projects.infrastructure.persistence.repository.SpringDataProjectMembershipRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Optional;

@Repository
public class JpaProjectMembershipRepositoryAdapter implements ProjectMembershipRepository {

    private final SpringDataProjectMembershipRepository repository;

    public JpaProjectMembershipRepositoryAdapter(SpringDataProjectMembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectMembership> findActive(CourseId courseId, StudentId studentId) {
        return repository.findByCourseIdAndStudentIdAndLeftAtIsNull(courseId.value(), studentId.value())
                .map(ProjectMembershipPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public void add(ProjectMembership membership) {
        if (!membership.isActive()) {
            throw new IllegalArgumentException("Only new active memberships can be added");
        }
        try {
            repository.insertActive(membership.getId().value(), membership.getCourseId().value(),
                    membership.getProjectId().value(), membership.getStudentId().value(), membership.getJoinedAt());
        } catch (DataIntegrityViolationException failure) {
            if (isUniqueViolation(failure)) {
                throw new ProjectMembershipConflictException("Membership already exists or student already has an active group in this course", failure);
            }
            throw failure;
        }
    }

    @Override
    @Transactional
    public void close(ProjectMembership membership) {
        var leftAt = membership.getLeftAt().orElseThrow(
                () -> new IllegalArgumentException("A closed membership is required"));
        int changed = repository.closeActive(membership.getId().value(), membership.getCourseId().value(),
                membership.getProjectId().value(), membership.getStudentId().value(), membership.getJoinedAt(), leftAt);
        if (changed != 1) {
            throw new ProjectMembershipConflictException("Membership is missing, changed or already closed");
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
