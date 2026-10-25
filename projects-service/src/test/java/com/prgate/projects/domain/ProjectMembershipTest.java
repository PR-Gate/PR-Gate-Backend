package com.prgate.projects.domain;

import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ProjectMembershipTest {

    private static final ProjectMembershipId MEMBERSHIP_ID = new ProjectMembershipId(
            UUID.fromString("b2f0955c-46de-48b6-bbe1-1eab360e5852"));
    private static final CourseId COURSE_ID = new CourseId(
            UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa"));
    private static final ProjectId PROJECT_ID = new ProjectId(
            UUID.fromString("8bfc2272-a520-4995-852e-38ef0576def1"));
    private static final StudentId STUDENT_ID = new StudentId(
            UUID.fromString("b4ed33f0-0987-4a1f-a988-5cd147ac861f"));
    private static final Instant JOINED_AT = Instant.parse("2026-10-24T15:00:00Z");
    private static final Instant LEFT_AT = JOINED_AT.plusSeconds(3600);

    @Test
    void joinsProjectAsActiveWithoutLeaveTime() {
        ProjectMembership membership = activeMembership();

        assertThat(membership.getId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(membership.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(membership.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(membership.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(membership.getJoinedAt()).isEqualTo(JOINED_AT);
        assertThat(membership.isActive()).isTrue();
        assertThat(membership.getLeftAt()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("missingMembershipData")
    void rejectsMissingMembershipData(ProjectMembershipId id, CourseId courseId, ProjectId projectId,
                                     StudentId studentId, Instant joinedAt, String message) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProjectMembership.join(id, courseId, projectId, studentId, joinedAt))
                .withMessage(message);
    }

    static Stream<Arguments> missingMembershipData() {
        return Stream.of(
                Arguments.of(null, COURSE_ID, PROJECT_ID, STUDENT_ID, JOINED_AT, "Project membership id is required"),
                Arguments.of(MEMBERSHIP_ID, null, PROJECT_ID, STUDENT_ID, JOINED_AT, "Course id is required"),
                Arguments.of(MEMBERSHIP_ID, COURSE_ID, null, STUDENT_ID, JOINED_AT, "Project id is required"),
                Arguments.of(MEMBERSHIP_ID, COURSE_ID, PROJECT_ID, null, JOINED_AT, "Student id is required"),
                Arguments.of(MEMBERSHIP_ID, COURSE_ID, PROJECT_ID, STUDENT_ID, null, "Join time is required")
        );
    }

    @Test
    void closesMembershipWithoutChangingHistoricalReferences() {
        ProjectMembership membership = activeMembership();

        membership.close(LEFT_AT);

        assertThat(membership.isActive()).isFalse();
        assertThat(membership.getLeftAt()).contains(LEFT_AT);
        assertThat(membership.getId()).isEqualTo(MEMBERSHIP_ID);
        assertThat(membership.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(membership.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(membership.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(membership.getJoinedAt()).isEqualTo(JOINED_AT);
    }

    @Test
    void acceptsClosingAtJoinTime() {
        ProjectMembership membership = activeMembership();

        membership.close(JOINED_AT);

        assertThat(membership.isActive()).isFalse();
        assertThat(membership.getLeftAt()).contains(JOINED_AT);
    }

    @Test
    void rejectsLeaveTimeBeforeJoinTimeWithoutChangingMembership() {
        ProjectMembership membership = activeMembership();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> membership.close(JOINED_AT.minusNanos(1)))
                .withMessage("Leave time must not precede join time");

        assertThat(membership.isActive()).isTrue();
        assertThat(membership.getLeftAt()).isEmpty();
    }

    @Test
    void rejectsMissingLeaveTimeWithoutChangingMembership() {
        ProjectMembership membership = activeMembership();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> membership.close(null))
                .withMessage("Leave time is required");

        assertThat(membership.isActive()).isTrue();
        assertThat(membership.getLeftAt()).isEmpty();
    }

    @Test
    void cannotCloseMembershipTwiceOrOverwriteItsLeaveTime() {
        ProjectMembership membership = activeMembership();
        membership.close(LEFT_AT);

        assertThatIllegalStateException()
                .isThrownBy(() -> membership.close(LEFT_AT.plusSeconds(60)))
                .withMessage("Only active project memberships can be closed");

        assertThat(membership.getLeftAt()).contains(LEFT_AT);
        assertThat(membership.isActive()).isFalse();
    }

    @Test
    void newParticipationDoesNotOverwriteClosedParticipation() {
        ProjectMembership previous = activeMembership();
        previous.close(LEFT_AT);
        ProjectMembershipId newId = new ProjectMembershipId(
                UUID.fromString("972f61dd-c5ce-47d0-81d0-5149d982e399"));
        ProjectId newProjectId = new ProjectId(UUID.fromString("d404b0ea-2b92-4f16-9c5f-c19d37b4c85b"));

        ProjectMembership next = ProjectMembership.join(newId, COURSE_ID, newProjectId, STUDENT_ID, LEFT_AT);

        assertThat(previous.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(previous.getLeftAt()).contains(LEFT_AT);
        assertThat(previous.isActive()).isFalse();
        assertThat(next.getId()).isNotEqualTo(previous.getId());
        assertThat(next.getProjectId()).isEqualTo(newProjectId);
        assertThat(next.getCourseId()).isEqualTo(previous.getCourseId());
        assertThat(next.getStudentId()).isEqualTo(previous.getStudentId());
        assertThat(next.getJoinedAt()).isEqualTo(LEFT_AT);
        assertThat(next.isActive()).isTrue();
    }

    private static ProjectMembership activeMembership() {
        return ProjectMembership.join(MEMBERSHIP_ID, COURSE_ID, PROJECT_ID, STUDENT_ID, JOINED_AT);
    }
}
