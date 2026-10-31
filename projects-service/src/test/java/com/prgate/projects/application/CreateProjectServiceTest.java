package com.prgate.projects.application;

import com.prgate.projects.application.command.CreateProjectCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CreateProjectResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.service.CreateProjectService;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
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
class CreateProjectServiceTest {

    private static final UUID TEACHER_ID = UUID.fromString("f66317a1-d467-4736-bc2e-e01f9499bc3f");
    private static final UUID COURSE_ID = UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa");
    private static final Actor ACTOR = new Actor(TEACHER_ID);
    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");
    private static final Course COURSE = Course.create(new CourseId(COURSE_ID), "Aplicaciones Web", "1234",
            "2026-2", new TeacherId(TEACHER_ID), NOW.minusSeconds(60));
    private static final CreateProjectCommand COMMAND = new CreateProjectCommand(COURSE_ID, "Grupo 1", "PRGate");

    @Mock
    private CourseRepository courses;

    @Mock
    private ProjectRepository projects;

    @Mock
    private UserDirectory users;

    private CreateProjectService service;

    @BeforeEach
    void setUp() {
        service = new CreateProjectService(courses, projects, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void responsibleTeacherCreatesProjectWithoutLeaderOrOrganization() {
        allowActiveTeacher();
        when(courses.findById(new CourseId(COURSE_ID))).thenReturn(Optional.of(COURSE));

        CreateProjectResult result = service.handle(new CreateProjectCommand(COURSE_ID, " Grupo 1 ", " PRGate "), ACTOR);

        ArgumentCaptor<Project> savedProject = ArgumentCaptor.forClass(Project.class);
        verify(projects).save(savedProject.capture());
        Project project = savedProject.getValue();
        assertThat(project.getId().value()).isNotNull();
        assertThat(project.getCourseId()).isEqualTo(COURSE.getId());
        assertThat(project.getLeaderId()).isEmpty();
        assertThat(project.getGitHubOrganization()).isEmpty();
        assertThat(result).isEqualTo(new CreateProjectResult(project.getId().value(), COURSE_ID,
                "Grupo 1", "PRGate", NOW));
    }

    @ParameterizedTest
    @CsvSource({"STUDENT, ACTIVE", "ADMIN, ACTIVE", "TEACHER, INACTIVE", "STUDENT, INACTIVE", "ADMIN, INACTIVE"})
    void rejectsUsersWhoAreNotActiveTeachers(UserAccount.Role role, UserAccount.Status status) {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.of(new UserAccount(TEACHER_ID, role, status)));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(courses, projects);
    }

    @Test
    void rejectsMissingUserBeforeConsultingCourses() {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(courses, projects);
    }

    @Test
    void propagatesUserDirectoryFailureWithoutSaving() {
        UserDirectoryException failure = new UserDirectoryException("Users is unavailable");
        when(users.findById(TEACHER_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verifyNoInteractions(courses, projects);
    }

    @Test
    void rejectsMismatchedUserIdentity() {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.of(new UserAccount(UUID.randomUUID(),
                UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(UserDirectoryException.class);

        verifyNoInteractions(courses, projects);
    }

    @Test
    void rejectsMissingCourseWithoutSavingProject() {
        allowActiveTeacher();
        when(courses.findById(new CourseId(COURSE_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isInstanceOf(CourseNotFoundException.class);

        verifyNoInteractions(projects);
    }

    @Test
    void anotherActiveTeacherCannotCreateProjectInCourse() {
        allowActiveTeacher();
        Course otherTeachersCourse = Course.create(new CourseId(COURSE_ID), "Web", "1234", "2026-2",
                new TeacherId(UUID.randomUUID()), NOW.minusSeconds(60));
        when(courses.findById(new CourseId(COURSE_ID))).thenReturn(Optional.of(otherTeachersCourse));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the responsible teacher can create projects in this course");

        verifyNoInteractions(projects);
    }

    @Test
    void propagatesCourseReadFailureWithoutTreatingItAsMissingCourse() {
        allowActiveTeacher();
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        when(courses.findById(new CourseId(COURSE_ID))).thenThrow(failure);

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);

        verifyNoInteractions(projects);
    }

    @Test
    void doesNotReturnSuccessWhenProjectPersistenceFails() {
        allowActiveTeacher();
        when(courses.findById(new CourseId(COURSE_ID))).thenReturn(Optional.of(COURSE));
        IllegalStateException failure = new IllegalStateException("Storage is unavailable");
        doThrow(failure).when(projects).save(any(Project.class));

        assertThatThrownBy(() -> service.handle(COMMAND, ACTOR)).isSameAs(failure);
    }

    @ParameterizedTest
    @MethodSource("invalidProjectData")
    void rejectsInvalidProjectDataBeforeSaving(CreateProjectCommand command) {
        allowActiveTeacher();
        when(courses.findById(new CourseId(COURSE_ID))).thenReturn(Optional.of(COURSE));

        assertThatThrownBy(() -> service.handle(command, ACTOR)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(projects);
    }

    static Stream<Arguments> invalidProjectData() {
        return Stream.of(
                Arguments.of(new CreateProjectCommand(COURSE_ID, " ", "PRGate")),
                Arguments.of(new CreateProjectCommand(COURSE_ID, "Grupo 1", "a".repeat(2001)))
        );
    }

    @Test
    void rejectsMissingCourseIdBeforeConsultingDependencies() {
        assertThatThrownBy(() -> service.handle(new CreateProjectCommand(null, "Grupo 1", ""), ACTOR))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(users, courses, projects);
    }

    private void allowActiveTeacher() {
        when(users.findById(TEACHER_ID)).thenReturn(Optional.of(
                new UserAccount(TEACHER_ID, UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));
    }
}
