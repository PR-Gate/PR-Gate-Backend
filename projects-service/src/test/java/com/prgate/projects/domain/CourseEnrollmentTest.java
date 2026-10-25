package com.prgate.projects.domain;

import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CourseEnrollmentTest {

    private static final CourseEnrollmentId ENROLLMENT_ID = new CourseEnrollmentId(
            UUID.fromString("95628d52-cd9f-4272-a5c9-d8dcb3149bd1"));
    private static final CourseId COURSE_ID = new CourseId(
            UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa"));
    private static final StudentId STUDENT_ID = new StudentId(
            UUID.fromString("b4ed33f0-0987-4a1f-a988-5cd147ac861f"));
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-24T15:00:00Z");
    private static final Instant RESOLVED_AT = REQUESTED_AT.plusSeconds(60);

    @Test
    void requestsAdmissionAsPendingWithoutResolution() {
        CourseEnrollment enrollment = pendingEnrollment();

        assertThat(enrollment.getId()).isEqualTo(ENROLLMENT_ID);
        assertThat(enrollment.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(enrollment.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(enrollment.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getResolvedAt()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("missingRequestData")
    void rejectsMissingRequestData(CourseEnrollmentId id, CourseId courseId, StudentId studentId,
                                  Instant requestedAt, String message) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CourseEnrollment.request(id, courseId, studentId, requestedAt))
                .withMessage(message);
    }

    static Stream<Arguments> missingRequestData() {
        return Stream.of(
                Arguments.of(null, COURSE_ID, STUDENT_ID, REQUESTED_AT, "Course enrollment id is required"),
                Arguments.of(ENROLLMENT_ID, null, STUDENT_ID, REQUESTED_AT, "Course id is required"),
                Arguments.of(ENROLLMENT_ID, COURSE_ID, null, REQUESTED_AT, "Student id is required"),
                Arguments.of(ENROLLMENT_ID, COURSE_ID, STUDENT_ID, null, "Request time is required")
        );
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"APPROVED", "REJECTED"})
    void resolvesPendingRequestAndPreservesItsIdentity(EnrollmentStatus decision) {
        CourseEnrollment enrollment = pendingEnrollment();

        resolve(enrollment, decision, RESOLVED_AT);

        assertThat(enrollment.getStatus()).isEqualTo(decision);
        assertThat(enrollment.getResolvedAt()).contains(RESOLVED_AT);
        assertThat(enrollment.getId()).isEqualTo(ENROLLMENT_ID);
        assertThat(enrollment.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(enrollment.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(enrollment.getRequestedAt()).isEqualTo(REQUESTED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"APPROVED", "REJECTED"})
    void acceptsResolutionAtTheRequestTime(EnrollmentStatus decision) {
        CourseEnrollment enrollment = pendingEnrollment();

        resolve(enrollment, decision, REQUESTED_AT);

        assertThat(enrollment.getStatus()).isEqualTo(decision);
        assertThat(enrollment.getResolvedAt()).contains(REQUESTED_AT);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED, APPROVED", "APPROVED, REJECTED", "REJECTED, APPROVED", "REJECTED, REJECTED"})
    void cannotResolveRequestTwice(EnrollmentStatus firstDecision, EnrollmentStatus secondDecision) {
        CourseEnrollment enrollment = pendingEnrollment();
        resolve(enrollment, firstDecision, RESOLVED_AT);

        assertThatIllegalStateException()
                .isThrownBy(() -> resolve(enrollment, secondDecision, RESOLVED_AT.plusSeconds(60)))
                .withMessage("Only pending enrollment requests can be resolved");

        assertThat(enrollment.getStatus()).isEqualTo(firstDecision);
        assertThat(enrollment.getResolvedAt()).contains(RESOLVED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"APPROVED", "REJECTED"})
    void rejectsMissingResolutionTimeWithoutChangingRequest(EnrollmentStatus decision) {
        CourseEnrollment enrollment = pendingEnrollment();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolve(enrollment, decision, null))
                .withMessage("Resolution time is required");

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getResolvedAt()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"APPROVED", "REJECTED"})
    void rejectsResolutionBeforeRequestWithoutChangingRequest(EnrollmentStatus decision) {
        CourseEnrollment enrollment = pendingEnrollment();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolve(enrollment, decision, REQUESTED_AT.minusNanos(1)))
                .withMessage("Resolution time must not precede request time");

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getResolvedAt()).isEmpty();
    }

    private static CourseEnrollment pendingEnrollment() {
        return CourseEnrollment.request(ENROLLMENT_ID, COURSE_ID, STUDENT_ID, REQUESTED_AT);
    }

    private static void resolve(CourseEnrollment enrollment, EnrollmentStatus decision, Instant resolvedAt) {
        switch (decision) {
            case APPROVED -> enrollment.approve(resolvedAt);
            case REJECTED -> enrollment.reject(resolvedAt);
            case PENDING -> throw new IllegalArgumentException("Pending is not a resolution");
        }
    }
}
