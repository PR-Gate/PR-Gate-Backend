package com.prgate.projects.domain;

import com.prgate.projects.domain.model.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CourseTest {

    private static final CourseId COURSE_ID = new CourseId(UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa"));
    private static final TeacherId TEACHER_ID = new TeacherId(UUID.fromString("f66317a1-d467-4736-bc2e-e01f9499bc3f"));
    private static final Instant CREATED_AT = Instant.parse("2026-10-24T15:00:00Z");

    @Test
    void createsCourseSectionForAcademicPeriodAndTeacher() {
        Course course = Course.create(COURSE_ID, "Aplicaciones Web", "1234", "2026-2", TEACHER_ID, CREATED_AT);

        assertThat(course.getId()).isEqualTo(COURSE_ID);
        assertThat(course.getName()).isEqualTo("Aplicaciones Web");
        assertThat(course.getSection()).isEqualTo("1234");
        assertThat(course.getAcademicPeriod()).isEqualTo("2026-2");
        assertThat(course.getTeacherId()).isEqualTo(TEACHER_ID);
        assertThat(course.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void removesSurroundingWhitespaceFromCourseDetails() {
        Course course = Course.create(COURSE_ID, "  Aplicaciones Web  ", "\t1234 ", " 2026-2\n", TEACHER_ID, CREATED_AT);

        assertThat(course.getName()).isEqualTo("Aplicaciones Web");
        assertThat(course.getSection()).isEqualTo("1234");
        assertThat(course.getAcademicPeriod()).isEqualTo("2026-2");
    }

    @ParameterizedTest
    @MethodSource("invalidCourseDetails")
    void rejectsInvalidCourseDetails(String name, String section, String period, String message) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Course.create(COURSE_ID, name, section, period, TEACHER_ID, CREATED_AT))
                .withMessage(message);
    }

    static Stream<Arguments> invalidCourseDetails() {
        return Stream.of(
                Arguments.of(null, "1234", "2026-2", "Course name is required"),
                Arguments.of("", "1234", "2026-2", "Course name is required"),
                Arguments.of("\u2003", "1234", "2026-2", "Course name is required"),
                Arguments.of("a".repeat(151), "1234", "2026-2", "Course name must not exceed 150 characters"),
                Arguments.of("Web", null, "2026-2", "Section is required"),
                Arguments.of("Web", "", "2026-2", "Section is required"),
                Arguments.of("Web", "\t\n", "2026-2", "Section is required"),
                Arguments.of("Web", "s".repeat(51), "2026-2", "Section must not exceed 50 characters"),
                Arguments.of("Web", "1234", null, "Academic period is required"),
                Arguments.of("Web", "1234", "", "Academic period is required"),
                Arguments.of("Web", "1234", " ", "Academic period is required"),
                Arguments.of("Web", "1234", "p".repeat(31), "Academic period must not exceed 30 characters")
        );
    }

    @Test
    void acceptsDetailsAtLengthLimitsAfterNormalization() {
        Course course = Course.create(COURSE_ID, " " + "a".repeat(150) + " ",
                " " + "s".repeat(50) + " ", " " + "p".repeat(30) + " ", TEACHER_ID, CREATED_AT);

        assertThat(course.getName()).hasSize(150);
        assertThat(course.getSection()).hasSize(50);
        assertThat(course.getAcademicPeriod()).hasSize(30);
    }

    @Test
    void rejectsMissingCourseId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Course.create(null, "Web", "1234", "2026-2", TEACHER_ID, CREATED_AT))
                .withMessage("Course id is required");
    }

    @Test
    void rejectsMissingTeacherId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Course.create(COURSE_ID, "Web", "1234", "2026-2", null, CREATED_AT))
                .withMessage("Teacher id is required");
    }

    @Test
    void rejectsMissingCreationTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Course.create(COURSE_ID, "Web", "1234", "2026-2", TEACHER_ID, null))
                .withMessage("Creation time is required");
    }
}
