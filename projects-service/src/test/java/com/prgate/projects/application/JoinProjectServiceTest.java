package com.prgate.projects.application;

import com.prgate.projects.application.command.JoinProjectCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.service.JoinProjectService;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JoinProjectServiceTest {

    private static final UUID STUDENT_UUID = UUID.fromString("b4ed33f0-0987-4a1f-a988-5cd147ac861f");
    private static final StudentId STUDENT_ID = new StudentId(STUDENT_UUID);
    private static final Actor ACTOR = new Actor(STUDENT_UUID);
    private static final CourseId COURSE_ID = new CourseId(UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa"));
    private static final ProjectId PROJECT_ID = new ProjectId(UUID.fromString("8bfc2272-a520-4995-852e-38ef0576def1"));
    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");
    private static final Project PROJECT = Project.create(PROJECT_ID, "Grupo 1", "", COURSE_ID, NOW.minusSeconds(3600));
    private static final JoinProjectCommand COMMAND = new JoinProjectCommand(PROJECT_ID.value());

    @Mock
    private ProjectRepository projects;
    @Mock
    private CourseEnrollmentRepository enrollments;
    @Mock
    private ProjectMembershipRepository memberships;
    @Mock
    private UserDirectory users;

    private JoinProjectService service;

    @BeforeEach
    void setUp() {
        service = new JoinProjectService(projects, enrollments, memberships, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void admittedStudentJoinsOwnProjectWithoutBecomingLeader() {
        allowAdmission();
        when(memberships.findActive(COURSE_ID, STUDENT_ID)).thenReturn(Optional.empty());

        ProjectMembershipResult result = service.handle(COMMAND, ACTOR);

        ArgumentCaptor<ProjectMembership> added = ArgumentCaptor.forClass(ProjectMembership.class);
        verify(memberships).add(added.capture());
        assertThat(result.id()).isEqualTo(added.getValue().getId().value());
        assertThat(result.studentId()).isEqualTo(ACTOR.userId());
        assertThat(result.courseId()).isEqualTo(PROJECT.getCourseId().value());
        assertThat(result.projectId()).isEqualTo(PROJECT_ID.value());
        assertThat(result.joinedAt()).isEqualTo(NOW);
        assertThat(result.leftAt()).isEmpty();
        assertThat(PROJECT.getLeaderId()).isEmpty();
        assertThat(PROJECT.getGitHubOrganization()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"TEACHER, ACTIVE", "ADMIN, ACTIVE", "STUDENT, INACTIVE", "TEACHER, INACTIVE", "ADMIN, INACTIVE"})
    void onlyActiveStudentsMayJoin(UserAccount.Role role, UserAccount.Status status) {
        when(users.findById(STUDENT_UUID)).thenReturn(Optional.of(new UserAccount(STUDENT_UUID, role, status)));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(projects, enrollments, memberships);
    }

    @Test
    void usersFailureDoesNotAllowMembershipCreation() {
        UserDirectoryException failure = new UserDirectoryException("Users is unavailable");
        when(users.findById(STUDENT_UUID)).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verifyNoInteractions(projects, enrollments, memberships);
    }

    @Test
    void missingProjectIsNotTreatedAsAnEmptyGroup() {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(ProjectNotFoundException.class);

        verifyNoInteractions(enrollments, memberships);
    }

    @Test
    void admissionToProjectCourseIsRequired() {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(PROJECT));
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(memberships);
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"PENDING", "REJECTED"})
    void nonApprovedEnrollmentCannotGrantAccess(EnrollmentStatus status) {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(PROJECT));
        CourseEnrollment enrollment = CourseEnrollment.reconstitute(new CourseEnrollmentId(UUID.randomUUID()),
                COURSE_ID, STUDENT_ID, NOW.minusSeconds(120), status,
                status == EnrollmentStatus.PENDING ? null : NOW.minusSeconds(60));
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(memberships);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cannotUseAnotherStudentsOrAnotherCoursesApproval(boolean wrongStudent) {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(PROJECT));
        CourseId courseId = wrongStudent ? COURSE_ID : new CourseId(UUID.randomUUID());
        StudentId studentId = wrongStudent ? new StudentId(UUID.randomUUID()) : STUDENT_ID;
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenReturn(Optional.of(approved(courseId, studentId, NOW.minusSeconds(60))));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(memberships);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void activeParticipationInSameOrAnotherProjectBlocksJoin(boolean sameProject) {
        allowAdmission();
        ProjectId existingProject = sameProject ? PROJECT_ID : new ProjectId(UUID.randomUUID());
        ProjectMembership existing = ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()), COURSE_ID,
                existingProject, STUDENT_ID, NOW.minusSeconds(30));
        when(memberships.findActive(COURSE_ID, STUDENT_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(ProjectMembershipConflictException.class);

        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getProjectId()).isEqualTo(existingProject);
        verify(memberships, never()).add(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void joinTimeCannotPrecedeApprovalOrProjectCreation(boolean futureApproval) {
        allowStudent();
        Project project = futureApproval ? PROJECT
                : Project.create(PROJECT_ID, "Grupo 1", "", COURSE_ID, NOW.plusSeconds(60));
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenReturn(Optional.of(
                approved(COURSE_ID, STUDENT_ID, futureApproval ? NOW.plusSeconds(60) : NOW.minusSeconds(60))));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(IllegalArgumentException.class);

        verify(memberships, never()).add(any());
    }

    @Test
    void permitsJoiningAtExactApprovalAndProjectCreationTime() {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(Project.create(PROJECT_ID, "Grupo 1", "", COURSE_ID, NOW)));
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenReturn(Optional.of(approved(COURSE_ID, STUDENT_ID, NOW)));

        assertThat(service.handle(COMMAND, ACTOR).joinedAt()).isEqualTo(NOW);
    }

    @Test
    void propagatesAtomicMembershipConflict() {
        allowAdmission();
        ProjectMembershipConflictException failure = new ProjectMembershipConflictException("Another membership won the race");
        doThrow(failure).when(memberships).add(any(ProjectMembership.class));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);
    }

    @Test
    void doesNotReturnSuccessIfMembershipStorageFails() {
        allowAdmission();
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        doThrow(failure).when(memberships).add(any(ProjectMembership.class));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);
    }

    @Test
    void projectReadFailureDoesNotGrantAccess() {
        allowStudent();
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        when(projects.findById(PROJECT_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verifyNoInteractions(enrollments, memberships);
    }

    @Test
    void approvalReadFailureDoesNotGrantAccess() {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(PROJECT));
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verifyNoInteractions(memberships);
    }

    @Test
    void membershipReadFailureIsNotTreatedAsNoParticipation() {
        allowAdmission();
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        when(memberships.findActive(COURSE_ID, STUDENT_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verify(memberships, never()).add(any());
    }

    @Test
    void rejectsMissingProjectIdBeforeCallingPorts() {
        assertThatThrownBy(() -> service.handle(new JoinProjectCommand(null), ACTOR)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(users, projects, enrollments, memberships);
    }

    private void allowStudent() {
        when(users.findById(STUDENT_UUID)).thenReturn(Optional.of(
                new UserAccount(STUDENT_UUID, UserAccount.Role.STUDENT, UserAccount.Status.ACTIVE)));
    }

    private void allowAdmission() {
        allowStudent();
        when(projects.findById(PROJECT_ID)).thenReturn(Optional.of(PROJECT));
        when(enrollments.findApproved(COURSE_ID, STUDENT_ID)).thenReturn(Optional.of(approved(COURSE_ID, STUDENT_ID, NOW.minusSeconds(60))));
    }

    private static CourseEnrollment approved(CourseId courseId, StudentId studentId, Instant approvedAt) {
        CourseEnrollment enrollment = CourseEnrollment.request(new CourseEnrollmentId(UUID.randomUUID()), courseId,
                studentId, NOW.minusSeconds(120));
        enrollment.approve(approvedAt);
        return enrollment;
    }
}
