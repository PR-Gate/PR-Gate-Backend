package com.prgate.projects.application;

import com.prgate.projects.application.command.CreateCourseCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.service.CreateCourseService;
import com.prgate.projects.domain.model.aggregates.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCourseServiceTest {

    private static final UUID TEACHER_ID = UUID.fromString("f66317a1-d467-4736-bc2e-e01f9499bc3f");
    private static final Actor ACTOR = new Actor(TEACHER_ID);
    private static final Instant NOW = Instant.parse("2026-10-24T15:00:00Z");
    private static final CreateCourseCommand COMMAND = new CreateCourseCommand("Aplicaciones Web", "1234", "2026-2");

    @Mock
    private CourseRepository courses;

    @Mock
    private UserDirectory users;

    private CreateCourseService service;

    @BeforeEach
    void setUp() {
        service = new CreateCourseService(courses, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsAndSavesCourseForAuthenticatedActiveTeacher() {
        allowActiveTeacher();

        CreateCourseResult result = service.handle(
                new CreateCourseCommand("  Aplicaciones Web  ", " 1234 ", " 2026-2 "), ACTOR);

        ArgumentCaptor<Course> savedCourse = ArgumentCaptor.forClass(Course.class);
        verify(courses).save(savedCourse.capture());
        verify(users).findById(TEACHER_ID);
        Course course = savedCourse.getValue();
        assertThat(course.getId().value()).isNotNull();
        assertThat(course.getTeacherId().value()).isEqualTo(ACTOR.userId());
        assertThat(course.getCreatedAt()).isEqualTo(NOW);
        assertThat(result).isEqualTo(new CreateCourseResult(course.getId().value(), "Aplicaciones Web",
                "1234", "2026-2", TEACHER_ID, NOW));
    }

    @ParameterizedTest
    @CsvSource({"STUDENT, ACTIVE", "ADMIN, ACTIVE", "TEACHER, INACTIVE", "STUDENT, INACTIVE", "ADMIN, INACTIVE"})
    void rejectsUsersWhoAreNotActiveTeachers(UserAccount.Role role, UserAccount.Status status) {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.of(new UserAccount(TEACHER_ID, role, status)));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("An active teacher is required");

        verifyNoInteractions(courses);
    }

    @Test
    void rejectsMissingUserWithoutSavingCourse() {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(courses);
    }

    @Test
    void propagatesUsersFailureWithoutTreatingItAsMissingUserOrSaving() {
        UserDirectoryException failure = new UserDirectoryException("Users is unavailable");
        when(users.findById(TEACHER_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verifyNoInteractions(courses);
    }

    @Test
    void rejectsResponseForAnotherUserWithoutSavingCourse() {
        UUID anotherUser = UUID.fromString("b4ed33f0-0987-4a1f-a988-5cd147ac861f");
        when(users.findById(TEACHER_ID)).thenReturn(Optional.of(
                new UserAccount(anotherUser, UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR))
                .isInstanceOf(UserDirectoryException.class);

        verifyNoInteractions(courses);
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void doesNotSaveWhenDomainRejectsCourseData(CreateCourseCommand command) {
        allowActiveTeacher();

        assertThatThrownBy(() -> service.handle(command, ACTOR)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(courses);
    }

    static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of(new CreateCourseCommand(" ", "1234", "2026-2")),
                Arguments.of(new CreateCourseCommand("Aplicaciones Web", null, "2026-2")),
                Arguments.of(new CreateCourseCommand("Aplicaciones Web", "1234", ""))
        );
    }

    @Test
    void doesNotReturnSuccessWhenPersistenceFails() {
        allowActiveTeacher();
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        doThrow(failure).when(courses).save(any(Course.class));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);
    }

    @Test
    void rejectsMissingActorBeforeConsultingDependencies() {
        assertThatThrownBy(() -> service.handle(COMMAND, null)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(users, courses);
    }

    @Test
    void rejectsMissingCommandBeforeConsultingDependencies() {
        assertThatThrownBy(() -> service.handle(null, ACTOR)).isInstanceOf(NullPointerException.class);

        verifyNoInteractions(users, courses);
    }

    private void allowActiveTeacher() {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.of(
                new UserAccount(TEACHER_ID, UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));
    }
}
