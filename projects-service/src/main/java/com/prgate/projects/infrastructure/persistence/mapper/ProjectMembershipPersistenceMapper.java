package com.prgate.projects.infrastructure.persistence.mapper;

import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.infrastructure.persistence.entity.ProjectMembershipJpaEntity;

public final class ProjectMembershipPersistenceMapper {
    private ProjectMembershipPersistenceMapper() { }

    public static ProjectMembership toDomain(ProjectMembershipJpaEntity entity) {
        return ProjectMembership.reconstitute(new ProjectMembershipId(entity.getId()),
                new CourseId(entity.getCourseId()), new ProjectId(entity.getProjectId()),
                new StudentId(entity.getStudentId()), entity.getJoinedAt(), entity.getLeftAt());
    }
}
