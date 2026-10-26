package com.prgate.projects.application.service;

import com.prgate.projects.application.command.LinkGitHubOrganizationCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.GitHubOrganizationNotFoundException;
import com.prgate.projects.application.exception.ProjectConflictException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.port.in.LinkGitHubOrganizationUseCase;
import com.prgate.projects.application.port.out.GitHubOrganizationDirectory;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.ProjectUnitOfWork;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.LinkedOrganizationResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import java.util.Objects;

public final class LinkGitHubOrganizationService implements LinkGitHubOrganizationUseCase {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final ProjectUnitOfWork unitOfWork;
    private final GitHubOrganizationDirectory organizations;
    private final UserAccessPolicy access;

    public LinkGitHubOrganizationService(ProjectRepository projects, ProjectMembershipRepository memberships,
                                         ProjectUnitOfWork unitOfWork, GitHubOrganizationDirectory organizations,
                                         UserDirectory users) {
        this.projects = Objects.requireNonNull(projects);
        this.memberships = Objects.requireNonNull(memberships);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.organizations = Objects.requireNonNull(organizations);
        this.access = new UserAccessPolicy(users);
    }

    @Override
    public LinkedOrganizationResult handle(LinkGitHubOrganizationCommand command, Actor actor) {
        Objects.requireNonNull(command);
        var projectId = new ProjectId(command.projectId());
        var studentId = access.requireActiveStudent(actor);
        String login = GitHubOrganization.normalizeLogin(command.organizationLogin());
        Project snapshot = projects.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project does not exist"));
        requireLeader(snapshot, studentId);
        // External lookup precedes the transaction; authorization is rechecked under the lock.
        var organization = organizations.findByLogin(login)
                .orElseThrow(() -> new GitHubOrganizationNotFoundException("GitHub organization does not exist"));
        return unitOfWork.execute(projectId, project -> {
            requireLeader(project, studentId);
            project.linkGitHubOrganization(organization, studentId);
            projects.save(project);
            return new LinkedOrganizationResult(projectId.value(), organization.id(), organization.login());
        });
    }

    private void requireLeader(Project project, StudentId studentId) {
        if (project.getLeaderId().filter(studentId::equals).isEmpty()
                || memberships.findActive(project.getCourseId(), studentId)
                    .filter(member -> member.getProjectId().equals(project.getId())).isEmpty()) {
            throw new AccessDeniedException("Only the current active group leader can register its organization");
        }
        if (project.getGitHubOrganization().isPresent()) {
            throw new ProjectConflictException("Group already has a linked organization");
        }
    }
}
