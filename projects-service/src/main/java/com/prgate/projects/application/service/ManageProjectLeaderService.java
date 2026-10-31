package com.prgate.projects.application.service;

import com.prgate.projects.application.command.DesignateProjectLeaderCommand;
import com.prgate.projects.application.command.RemoveProjectLeaderCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.port.in.DesignateProjectLeaderUseCase;
import com.prgate.projects.application.port.in.RemoveProjectLeaderUseCase;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.ProjectUnitOfWork;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.ProjectLeadershipResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.application.security.ResponsibleTeacherPolicy;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import java.util.Objects;
import java.util.Optional;

public final class ManageProjectLeaderService implements DesignateProjectLeaderUseCase, RemoveProjectLeaderUseCase {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final ProjectUnitOfWork unitOfWork;
    private final UserAccessPolicy access;
    private final ResponsibleTeacherPolicy teacherPolicy;

    public ManageProjectLeaderService(ProjectRepository projects, ProjectMembershipRepository memberships,
                                      CourseRepository courses, ProjectUnitOfWork unitOfWork, UserDirectory users) {
        this.projects = Objects.requireNonNull(projects);
        this.memberships = Objects.requireNonNull(memberships);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.access = new UserAccessPolicy(users);
        this.teacherPolicy = new ResponsibleTeacherPolicy(courses);
    }

    @Override
    public ProjectLeadershipResult handle(DesignateProjectLeaderCommand command, Actor actor) {
        Objects.requireNonNull(command);
        var projectId = new ProjectId(command.projectId());
        var teacherId = access.requireActiveTeacher(actor);
        var studentId = access.requireActiveStudent(new StudentId(command.studentId()));
        return unitOfWork.execute(projectId, project -> {
            teacherPolicy.requireResponsible(project.getCourseId(), teacherId);
            memberships.findActive(project.getCourseId(), studentId)
                    .filter(member -> member.getProjectId().equals(projectId))
                    .orElseThrow(() -> new AccessDeniedException("Leader must be an active member of this group"));
            project.designateLeader(studentId);
            projects.save(project);
            return new ProjectLeadershipResult(projectId.value(), Optional.of(studentId.value()));
        });
    }

    @Override
    public ProjectLeadershipResult handle(RemoveProjectLeaderCommand command, Actor actor) {
        Objects.requireNonNull(command);
        var teacherId = access.requireActiveTeacher(actor);
        return unitOfWork.execute(new ProjectId(command.projectId()), project -> {
            teacherPolicy.requireResponsible(project.getCourseId(), teacherId);
            project.removeLeader();
            projects.save(project);
            return new ProjectLeadershipResult(project.getId().value(), Optional.empty());
        });
    }
}
