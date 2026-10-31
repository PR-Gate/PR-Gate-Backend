package com.prgate.projects.infrastructure.persistence.mapper;

import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.infrastructure.persistence.entity.ProjectJpaEntity;

public final class ProjectPersistenceMapper {

    private ProjectPersistenceMapper() {
    }

    public static ProjectJpaEntity toEntity(Project project) {
        return new ProjectJpaEntity(project.getId().value(), project.getCourseId().value(), project.getName(),
                project.getDescription(), project.getCreatedAt(), project.getLeaderId().map(StudentId::value).orElse(null),
                project.getGitHubOrganization().map(GitHubOrganization::id).orElse(null),
                project.getGitHubOrganization().map(GitHubOrganization::login).orElse(null));
    }

    public static Project toDomain(ProjectJpaEntity entity) {
        Long organizationId = entity.getGitHubOrganizationId();
        String organizationLogin = entity.getGitHubOrganizationLogin();
        if ((organizationId == null) != (organizationLogin == null)) {
            throw new IllegalStateException("Stored GitHub organization metadata is incomplete");
        }
        GitHubOrganization organization = organizationId == null ? null
                : new GitHubOrganization(organizationId, organizationLogin);
        StudentId leaderId = entity.getLeaderId() == null ? null : new StudentId(entity.getLeaderId());
        return Project.reconstitute(new ProjectId(entity.getId()), entity.getName(), entity.getDescription(),
                new CourseId(entity.getCourseId()), entity.getCreatedAt(), leaderId, organization);
    }
}
