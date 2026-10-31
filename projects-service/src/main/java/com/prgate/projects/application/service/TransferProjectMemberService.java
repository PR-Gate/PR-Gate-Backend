package com.prgate.projects.application.service;

import com.prgate.projects.application.command.TransferProjectMemberCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.port.in.TransferProjectMemberUseCase;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.ProjectUnitOfWork;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.application.security.ResponsibleTeacherPolicy;
import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class TransferProjectMemberService implements TransferProjectMemberUseCase {
    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final CourseEnrollmentRepository enrollments;
    private final ProjectUnitOfWork unitOfWork;
    private final UserAccessPolicy access;
    private final ResponsibleTeacherPolicy teacherPolicy;
    private final Clock clock;

    public TransferProjectMemberService(ProjectRepository projects, ProjectMembershipRepository memberships,
                                        CourseEnrollmentRepository enrollments, CourseRepository courses,
                                        ProjectUnitOfWork unitOfWork, UserDirectory users, Clock clock) {
        this.projects = Objects.requireNonNull(projects);
        this.memberships = Objects.requireNonNull(memberships);
        this.enrollments = Objects.requireNonNull(enrollments);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.access = new UserAccessPolicy(users);
        this.teacherPolicy = new ResponsibleTeacherPolicy(courses);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ProjectMembershipResult handle(TransferProjectMemberCommand command, Actor actor) {
        Objects.requireNonNull(command);
        var sourceId = new ProjectId(command.sourceProjectId());
        var targetId = new ProjectId(command.targetProjectId());
        var studentId = new StudentId(command.studentId());
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Transfer requires a different target group");
        }
        var teacherId = access.requireActiveTeacher(actor);
        return unitOfWork.execute(sourceId, source -> {
            teacherPolicy.requireResponsible(source.getCourseId(), teacherId);
            var target = projects.findById(targetId)
                    .orElseThrow(() -> new ProjectNotFoundException("Target project does not exist"));
            if (!source.getCourseId().equals(target.getCourseId())) {
                throw new IllegalArgumentException("Transfer must stay within the same course");
            }
            var admission = enrollments.findApproved(source.getCourseId(), studentId)
                    .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.APPROVED
                            && enrollment.getCourseId().equals(source.getCourseId())
                            && enrollment.getStudentId().equals(studentId))
                    .orElseThrow(() -> new AccessDeniedException("Approved admission is required"));
            var previous = memberships.findActive(source.getCourseId(), studentId)
                    .filter(member -> member.getProjectId().equals(sourceId))
                    .orElseThrow(() -> new ProjectMembershipConflictException("Student no longer belongs to the source group"));
            var transferredAt = clock.instant();
            if (transferredAt.isBefore(target.getCreatedAt())
                    || transferredAt.isBefore(admission.getResolvedAt().orElseThrow())) {
                throw new IllegalArgumentException("Transfer must not precede target creation or admission");
            }
            previous.close(transferredAt);
            var next = ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()), source.getCourseId(),
                    targetId, studentId, transferredAt);
            memberships.close(previous);
            if (source.getLeaderId().filter(studentId::equals).isPresent()) {
                source.removeLeader();
                projects.save(source);
            }
            memberships.add(next);
            return ProjectMembershipResult.from(next);
        });
    }
}
