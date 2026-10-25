package com.prgate.projects.application;

import com.prgate.projects.application.command.RequestCourseEnrollmentCommand;
import com.prgate.projects.application.command.ResolveCourseEnrollmentCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.exception.EnrollmentConflictException;
import com.prgate.projects.application.exception.EnrollmentNotFoundException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import com.prgate.projects.application.model.EnrollmentDecision;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.service.RequestCourseEnrollmentService;
import com.prgate.projects.application.service.ResolveCourseEnrollmentService;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
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
class CourseEnrollmentServicesTest {

    private static final UUID STUDENT_UUID = UUID.fromString("b4ed33f0-0987-4a1f-a988-5cd147ac861f");
    private static final UUID TEACHER_UUID = UUID.fromString("f66317a1-d467-4736-bc2e-e01f9499bc3f");
    private static final Actor STUDENT = new Actor(STUDENT_UUID);
    private static final Actor TEACHER = new Actor(TEACHER_UUID);
    private static final CourseId COURSE_ID = new CourseId(UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa"));
    private static final StudentId STUDENT_ID = new StudentId(STUDENT_UUID);
    private static final CourseEnrollmentId ENROLLMENT_ID = new CourseEnrollmentId(
            UUID.fromString("95628d52-cd9f-4272-a5c9-d8dcb3149bd1"));
    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");
    private static final Course COURSE = Course.create(COURSE_ID, "Aplicaciones Web", "1234", "2026-2",
            new TeacherId(TEACHER_UUID), NOW.minusSeconds(3600));
    private static final RequestCourseEnrollmentCommand REQUEST = new RequestCourseEnrollmentCommand(COURSE_ID.value());

    @Mock
    private CourseRepository courses;
    @Mock
    private CourseEnrollmentRepository enrollments;
    @Mock
    private UserDirectory users;

    private RequestCourseEnrollmentService requestService;
    private ResolveCourseEnrollmentService resolveService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        requestService = new RequestCourseEnrollmentService(courses, enrollments, users, clock);
        resolveService = new ResolveCourseEnrollmentService(courses, enrollments, users, clock);
    }

    @Test
    void studentRequestsOwnAdmissionWithServerGeneratedIdentityAndTime() {
        allowStudent();
        when(courses.findById(COURSE_ID)).thenReturn(Optional.of(COURSE));
        when(enrollments.existsPendingOrApproved(COURSE_ID, STUDENT_ID)).thenReturn(false);

        CourseEnrollmentResult result = requestService.handle(REQUEST, STUDENT);

        ArgumentCaptor<CourseEnrollment> added = ArgumentCaptor.forClass(CourseEnrollment.class);
        verify(enrollments).add(added.capture());
        assertThat(result.id()).isEqualTo(added.getValue().getId().value());
        assertThat(result.studentId()).isEqualTo(STUDENT_UUID);
        assertThat(result.courseId()).isEqualTo(COURSE_ID.value());
        assertThat(result.status()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(result.requestedAt()).isEqualTo(NOW);
        assertThat(result.resolvedAt()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"TEACHER, ACTIVE", "ADMIN, ACTIVE", "STUDENT, INACTIVE", "TEACHER, INACTIVE", "ADMIN, INACTIVE"})
    void onlyActiveStudentsCanRequestAdmission(UserAccount.Role role, UserAccount.Status status) {
        when(users.findById(STUDENT_UUID)).thenReturn(Optional.of(new UserAccount(STUDENT_UUID, role, status)));

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void rejectsRequestForMissingCourse() {
        allowStudent();
        when(courses.findById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isInstanceOf(CourseNotFoundException.class);

        verifyNoInteractions(enrollments);
    }

    @Test
    void pendingOrApprovedEnrollmentBlocksAnotherRequest() {
        allowStudent();
        when(courses.findById(COURSE_ID)).thenReturn(Optional.of(COURSE));
        when(enrollments.existsPendingOrApproved(COURSE_ID, STUDENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isInstanceOf(EnrollmentConflictException.class);

        verify(enrollments, never()).add(any());
    }

    @Test
    void missingStudentCannotRequestAdmission() {
        when(users.findById(STUDENT_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void usersFailureIsNotTreatedAsMissingStudent() {
        UserDirectoryException failure = new UserDirectoryException("Users is unavailable");
        when(users.findById(STUDENT_UUID)).thenThrow(failure);

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isSameAs(failure);

        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void rejectsResponseForAnotherStudent() {
        when(users.findById(STUDENT_UUID)).thenReturn(Optional.of(
                new UserAccount(TEACHER_UUID, UserAccount.Role.STUDENT, UserAccount.Status.ACTIVE)));

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isInstanceOf(UserDirectoryException.class);

        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void requestPropagatesCourseReadFailure() {
        allowStudent();
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        when(courses.findById(COURSE_ID)).thenThrow(failure);

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isSameAs(failure);

        verifyNoInteractions(enrollments);
    }

    @Test
    void requestPropagatesAtomicInsertConflict() {
        allowStudent();
        when(courses.findById(COURSE_ID)).thenReturn(Optional.of(COURSE));
        EnrollmentConflictException conflict = new EnrollmentConflictException("Another request was inserted concurrently");
        doThrow(conflict).when(enrollments).add(any(CourseEnrollment.class));

        assertThatThrownBy(() -> requestService.handle(REQUEST, STUDENT)).isSameAs(conflict);
    }

    @Test
    void requestRejectsMissingCourseIdBeforeCallingPorts() {
        assertThatThrownBy(() -> requestService.handle(new RequestCourseEnrollmentCommand(null), STUDENT))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(users, courses, enrollments);
    }

    @ParameterizedTest
    @EnumSource(EnrollmentDecision.class)
    void responsibleTeacherResolvesPendingEnrollment(EnrollmentDecision decision) {
        CourseEnrollment enrollment = pendingEnrollment();
        allowResolution(enrollment);

        CourseEnrollmentResult result = resolveService.handle(command(decision), TEACHER);

        verify(enrollments).resolve(enrollment);
        assertThat(result.id()).isEqualTo(ENROLLMENT_ID.value());
        assertThat(result.studentId()).isEqualTo(STUDENT_UUID);
        assertThat(result.courseId()).isEqualTo(COURSE_ID.value());
        assertThat(result.status()).isEqualTo(decision == EnrollmentDecision.APPROVE
                ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        assertThat(result.requestedAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(result.resolvedAt()).contains(NOW);
    }

    @ParameterizedTest
    @EnumSource(EnrollmentDecision.class)
    void anotherTeacherCannotResolveEnrollment(EnrollmentDecision decision) {
        CourseEnrollment enrollment = pendingEnrollment();
        allowTeacher();
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        Course otherTeachersCourse = Course.create(COURSE_ID, "Web", "1234", "2026-2",
                new TeacherId(STUDENT_UUID), NOW.minusSeconds(3600));
        when(courses.findById(COURSE_ID)).thenReturn(Optional.of(otherTeachersCourse));

        assertThatThrownBy(() -> resolveService.handle(command(decision), TEACHER))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        verify(enrollments, never()).resolve(any());
    }

    @ParameterizedTest
    @CsvSource({"STUDENT, ACTIVE", "ADMIN, ACTIVE", "TEACHER, INACTIVE", "STUDENT, INACTIVE", "ADMIN, INACTIVE"})
    void onlyActiveTeachersCanResolveRequests(UserAccount.Role role, UserAccount.Status status) {
        when(users.findById(TEACHER_UUID)).thenReturn(Optional.of(new UserAccount(TEACHER_UUID, role, status)));

        assertThatThrownBy(() -> resolveService.handle(command(EnrollmentDecision.APPROVE), TEACHER))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(courses, enrollments);
    }

    @Test
    void rejectsResolutionOfMissingRequest() {
        allowTeacher();
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolveService.handle(command(EnrollmentDecision.APPROVE), TEACHER))
                .isInstanceOf(EnrollmentNotFoundException.class);

        verifyNoInteractions(courses);
        verify(enrollments, never()).resolve(any());
    }

    @Test
    void rejectsResolutionIfCourseIsMissing() {
        allowTeacher();
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(pendingEnrollment()));
        when(courses.findById(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolveService.handle(command(EnrollmentDecision.REJECT), TEACHER))
                .isInstanceOf(CourseNotFoundException.class);

        verify(enrollments, never()).resolve(any());
    }

    @ParameterizedTest
    @CsvSource({"APPROVED, APPROVE", "APPROVED, REJECT", "REJECTED, APPROVE", "REJECTED, REJECT"})
    void alreadyResolvedRequestCannotBeOverwritten(EnrollmentStatus status, EnrollmentDecision decision) {
        CourseEnrollment enrollment = pendingEnrollment();
        Instant previousResolution = NOW.minusSeconds(30);
        if (status == EnrollmentStatus.APPROVED) {
            enrollment.approve(previousResolution);
        } else {
            enrollment.reject(previousResolution);
        }
        allowResolution(enrollment);

        assertThatThrownBy(() -> resolveService.handle(command(decision), TEACHER))
                .isInstanceOf(IllegalStateException.class);

        assertThat(enrollment.getStatus()).isEqualTo(status);
        assertThat(enrollment.getResolvedAt()).contains(previousResolution);
        verify(enrollments, never()).resolve(any());
    }

    @Test
    void resolutionPropagatesConcurrentDecisionConflict() {
        CourseEnrollment enrollment = pendingEnrollment();
        allowResolution(enrollment);
        EnrollmentConflictException conflict = new EnrollmentConflictException("Request is no longer pending");
        doThrow(conflict).when(enrollments).resolve(enrollment);

        assertThatThrownBy(() -> resolveService.handle(command(EnrollmentDecision.APPROVE), TEACHER))
                .isSameAs(conflict);
    }

    @Test
    void resolutionDoesNotReturnSuccessIfStorageFails() {
        CourseEnrollment enrollment = pendingEnrollment();
        allowResolution(enrollment);
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        doThrow(failure).when(enrollments).resolve(enrollment);

        assertThatThrownBy(() -> resolveService.handle(command(EnrollmentDecision.REJECT), TEACHER)).isSameAs(failure);
    }

    @Test
    void rejectsMissingDecisionBeforeCallingPorts() {
        assertThatThrownBy(() -> resolveService.handle(new ResolveCourseEnrollmentCommand(ENROLLMENT_ID.value(), null), TEACHER))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(users, courses, enrollments);
    }

    @Test
    void rejectsMissingEnrollmentIdBeforeCallingPorts() {
        assertThatThrownBy(() -> resolveService.handle(new ResolveCourseEnrollmentCommand(null, EnrollmentDecision.APPROVE), TEACHER))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(users, courses, enrollments);
    }

    private void allowStudent() {
        when(users.findById(STUDENT_UUID)).thenReturn(Optional.of(
                new UserAccount(STUDENT_UUID, UserAccount.Role.STUDENT, UserAccount.Status.ACTIVE)));
    }

    private void allowTeacher() {
        when(users.findById(TEACHER_UUID)).thenReturn(Optional.of(
                new UserAccount(TEACHER_UUID, UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));
    }

    private void allowResolution(CourseEnrollment enrollment) {
        allowTeacher();
        when(enrollments.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(courses.findById(COURSE_ID)).thenReturn(Optional.of(COURSE));
    }

    private static CourseEnrollment pendingEnrollment() {
        return CourseEnrollment.request(ENROLLMENT_ID, COURSE_ID, STUDENT_ID, NOW.minusSeconds(60));
    }

    private static ResolveCourseEnrollmentCommand command(EnrollmentDecision decision) {
        return new ResolveCourseEnrollmentCommand(ENROLLMENT_ID.value(), decision);
    }
}
