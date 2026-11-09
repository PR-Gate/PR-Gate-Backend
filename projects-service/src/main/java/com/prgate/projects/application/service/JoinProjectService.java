package com.prgate.projects.application.service;

import com.prgate.projects.application.command.JoinProjectCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.port.in.JoinProjectUseCase;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class JoinProjectService implements JoinProjectUseCase {

    private final ProjectRepository projects;
    private final CourseEnrollmentRepository enrollments;
    private final ProjectMembershipRepository memberships;
    private final UserAccessPolicy accessPolicy;
    private final Clock clock;

    public JoinProjectService(ProjectRepository projects, CourseEnrollmentRepository enrollments,
                              ProjectMembershipRepository memberships, UserDirectory users, Clock clock) {
        this.projects = Objects.requireNonNull(projects, "Project repository is required");
        this.enrollments = Objects.requireNonNull(enrollments, "Enrollment repository is required");
        this.memberships = Objects.requireNonNull(memberships, "Membership repository is required");
        this.accessPolicy = new UserAccessPolicy(users);
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public ProjectMembershipResult handle(JoinProjectCommand command, Actor actor) {
        Objects.requireNonNull(command, "Join project command is required");
        Objects.requireNonNull(actor, "Authenticated actor is required");
        ProjectId projectId = new ProjectId(command.projectId());
        StudentId studentId = accessPolicy.requireActiveStudent(actor);
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("Project does not exist"));
        CourseEnrollment admission = enrollments.findApproved(project.getCourseId(), studentId)
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.APPROVED
                        && enrollment.getCourseId().equals(project.getCourseId())
                        && enrollment.getStudentId().equals(studentId))
                .orElseThrow(() -> new AccessDeniedException("Approved admission to the project's course is required"));
        if (memberships.findActive(project.getCourseId(), studentId).isPresent()) {
            throw new ProjectMembershipConflictException("The student already belongs to a project in this course");
        }
        Instant joinedAt = clock.instant();
        if (joinedAt.isBefore(admission.getResolvedAt().orElseThrow()) || joinedAt.isBefore(project.getCreatedAt())) {
            throw new IllegalArgumentException("Join time must not precede admission approval or project creation");
        }

        ProjectMembership membership = ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()),
                project.getCourseId(), project.getId(), studentId, joinedAt);
        memberships.add(membership);
        return ProjectMembershipResult.from(membership);
    }
}
