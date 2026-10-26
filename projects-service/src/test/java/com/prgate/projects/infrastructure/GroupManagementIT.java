package com.prgate.projects.infrastructure;

import com.prgate.projects.application.command.DesignateProjectLeaderCommand;
import com.prgate.projects.application.command.LinkGitHubOrganizationCommand;
import com.prgate.projects.application.command.RemoveProjectLeaderCommand;
import com.prgate.projects.application.command.TransferProjectMemberCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.GitHubDirectoryException;
import com.prgate.projects.application.exception.GitHubOrganizationNotFoundException;
import com.prgate.projects.application.exception.ProjectConflictException;
import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.GitHubOrganizationDirectory;
import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.ProjectUnitOfWork;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.service.LinkGitHubOrganizationService;
import com.prgate.projects.application.service.ManageProjectLeaderService;
import com.prgate.projects.application.service.TransferProjectMemberService;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import com.prgate.projects.infrastructure.persistence.adapter.JpaCourseEnrollmentRepositoryAdapter;
import com.prgate.projects.infrastructure.persistence.adapter.JpaCourseRepositoryAdapter;
import com.prgate.projects.infrastructure.persistence.adapter.JpaProjectMembershipRepositoryAdapter;
import com.prgate.projects.infrastructure.persistence.adapter.JpaProjectRepositoryAdapter;
import com.prgate.projects.infrastructure.persistence.adapter.JpaProjectUnitOfWork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.AdditionalAnswers.delegatesTo;

@DataJpaTest(properties = "spring.datasource.hikari.connection-init-sql=SET statement_timeout TO '10s'")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaCourseRepositoryAdapter.class, JpaProjectRepositoryAdapter.class,
        JpaCourseEnrollmentRepositoryAdapter.class, JpaProjectMembershipRepositoryAdapter.class,
        JpaProjectUnitOfWork.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class GroupManagementIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired private CourseRepository courses;
    @Autowired private ProjectRepository projects;
    @Autowired private CourseEnrollmentRepository enrollments;
    @Autowired private ProjectMembershipRepository memberships;
    @Autowired private ProjectUnitOfWork transactions;
    @Autowired private JdbcTemplate jdbc;
    private final Instant now = Instant.parse("2026-10-25T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private UserDirectory users;
    private GitHubOrganizationDirectory github;
    private Actor teacher;
    private StudentId student;
    private Course course;
    private Project source;
    private Project target;
    private ManageProjectLeaderService leaders;
    private LinkGitHubOrganizationService organizations;
    private TransferProjectMemberService transfers;

    @BeforeEach
    void setup() {
        users = mock(UserDirectory.class);
        github = mock(GitHubOrganizationDirectory.class);
        teacher = new Actor(UUID.randomUUID());
        student = new StudentId(UUID.randomUUID());
        when(users.findById(teacher.userId())).thenReturn(Optional.of(new UserAccount(teacher.userId(), UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));
        when(users.findById(student.value())).thenReturn(Optional.of(new UserAccount(student.value(), UserAccount.Role.STUDENT, UserAccount.Status.ACTIVE)));
        course = Course.create(new CourseId(UUID.randomUUID()), "Software", "01", "2026-2", new TeacherId(teacher.userId()), now.minusSeconds(100));
        courses.save(course);
        source = createProject(course.getId());
        target = createProject(course.getId());
        var admission = CourseEnrollment.request(new CourseEnrollmentId(UUID.randomUUID()), course.getId(), student, now.minusSeconds(90));
        enrollments.add(admission);
        admission.approve(now.minusSeconds(80));
        enrollments.resolve(admission);
        memberships.add(ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()), course.getId(), source.getId(), student, now.minusSeconds(70)));
        leaders = new ManageProjectLeaderService(projects, memberships, courses, transactions, users);
        organizations = new LinkGitHubOrganizationService(projects, memberships, transactions, github, users);
        transfers = new TransferProjectMemberService(projects, memberships, enrollments, courses, transactions, users, clock);
    }

    private Project createProject(CourseId courseId) {
        var project = Project.create(new ProjectId(UUID.randomUUID()), "Group", "", courseId, now.minusSeconds(95));
        projects.save(project);
        return project;
    }

    private void designate() {
        leaders.handle(new DesignateProjectLeaderCommand(source.getId().value(), student.value()), teacher);
    }

    private void link() {
        when(github.findByLogin("prgate-team")).thenReturn(Optional.of(new GitHubOrganization(42, "prgate-team")));
        organizations.handle(new LinkGitHubOrganizationCommand(source.getId().value(), " PRGate-Team "), new Actor(student.value()));
    }

    private TransferProjectMemberCommand transferCommand() {
        return new TransferProjectMemberCommand(source.getId().value(), target.getId().value(), student.value());
    }

    @Test
    void teacherDesignatesReplacesAndRemovesLeaderKeepingOrganization() {
        designate();
        link();
        StudentId other = new StudentId(UUID.randomUUID());
        when(users.findById(other.value())).thenReturn(Optional.of(new UserAccount(other.value(), UserAccount.Role.STUDENT, UserAccount.Status.ACTIVE)));
        memberships.add(ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()), course.getId(), source.getId(), other, now));
        leaders.handle(new DesignateProjectLeaderCommand(source.getId().value(), other.value()), teacher);
        assertThat(projects.findById(source.getId()).orElseThrow().getLeaderId()).contains(other);
        leaders.handle(new RemoveProjectLeaderCommand(source.getId().value()), teacher);
        leaders.handle(new RemoveProjectLeaderCommand(source.getId().value()), teacher);
        var stored = projects.findById(source.getId()).orElseThrow();
        assertThat(stored.getLeaderId()).isEmpty();
        assertThat(stored.getGitHubOrganization()).contains(new GitHubOrganization(42, "prgate-team"));
    }

    @Test
    void cannotDesignateMemberOfAnotherGroup() {
        assertThatThrownBy(() -> leaders.handle(new DesignateProjectLeaderCommand(target.getId().value(), student.value()), teacher))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(projects.findById(target.getId()).orElseThrow().getLeaderId()).isEmpty();
    }

    @Test
    void rejectsInactiveLeaderCandidate() {
        when(users.findById(student.value())).thenReturn(Optional.of(new UserAccount(student.value(), UserAccount.Role.STUDENT, UserAccount.Status.INACTIVE)));
        assertThatThrownBy(this::designate).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsOtherTeacherForAllManagementActions() {
        Actor stranger = new Actor(UUID.randomUUID());
        when(users.findById(stranger.userId())).thenReturn(Optional.of(new UserAccount(stranger.userId(), UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE)));
        assertThatThrownBy(() -> leaders.handle(new DesignateProjectLeaderCommand(source.getId().value(), student.value()), stranger)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaders.handle(new RemoveProjectLeaderCommand(source.getId().value()), stranger)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> transfers.handle(transferCommand(), stranger)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void studentCannotManageLeadershipOrTransfer() {
        var actor = new Actor(student.value());
        assertThatThrownBy(() -> leaders.handle(new DesignateProjectLeaderCommand(source.getId().value(), student.value()), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> leaders.handle(new RemoveProjectLeaderCommand(source.getId().value()), actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> transfers.handle(transferCommand(), actor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyLeaderCanLinkAndCannotReplaceExistingOrganization() {
        var command = new LinkGitHubOrganizationCommand(source.getId().value(), "prgate-team");
        assertThatThrownBy(() -> organizations.handle(command, new Actor(student.value()))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(github);
        designate();
        link();
        assertThatThrownBy(() -> organizations.handle(command, new Actor(student.value()))).isInstanceOf(ProjectConflictException.class);
        verify(github, times(1)).findByLogin("prgate-team");
    }

    @Test
    void missingOrganizationOrUnavailableGitHubDoesNotChangeProject() {
        designate();
        var command = new LinkGitHubOrganizationCommand(source.getId().value(), "missing");
        when(github.findByLogin("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> organizations.handle(command, new Actor(student.value()))).isInstanceOf(GitHubOrganizationNotFoundException.class);
        when(github.findByLogin("missing")).thenThrow(new GitHubDirectoryException("Unavailable"));
        assertThatThrownBy(() -> organizations.handle(command, new Actor(student.value()))).isInstanceOf(GitHubDirectoryException.class);
        assertThat(projects.findById(source.getId()).orElseThrow().getGitHubOrganization()).isEmpty();
    }

    @Test
    void rechecksLeadershipAfterGitHubLookup() {
        designate();
        when(github.findByLogin("prgate-team")).thenAnswer(call -> {
            leaders.handle(new RemoveProjectLeaderCommand(source.getId().value()), teacher);
            return Optional.of(new GitHubOrganization(42, "prgate-team"));
        });
        assertThatThrownBy(() -> organizations.handle(new LinkGitHubOrganizationCommand(source.getId().value(), "prgate-team"), new Actor(student.value())))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(projects.findById(source.getId()).orElseThrow().getGitHubOrganization()).isEmpty();
    }

    @Test
    void transferPreservesHistoryAndOrganizationAndClearsFormerLeadership() {
        designate();
        link();
        var previous = memberships.findActive(course.getId(), student).orElseThrow();
        var result = transfers.handle(transferCommand(), teacher);
        assertThat(result.projectId()).isEqualTo(target.getId().value());
        assertThat(result.joinedAt()).isEqualTo(now);
        assertThat(result.id()).isNotEqualTo(previous.getId().value());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_memberships WHERE course_id = ? AND student_id = ?", Integer.class, course.getId().value(), student.value())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT left_at FROM project_memberships WHERE id = ?", java.sql.Timestamp.class, previous.getId().value()).toInstant()).isEqualTo(now);
        var stored = projects.findById(source.getId()).orElseThrow();
        assertThat(stored.getLeaderId()).isEmpty();
        assertThat(stored.getGitHubOrganization()).isPresent();
        assertThat(projects.findById(target.getId()).orElseThrow().getLeaderId()).isEmpty();
        assertThatThrownBy(() -> transfers.handle(transferCommand(), teacher)).isInstanceOf(ProjectMembershipConflictException.class);
    }

    @Test
    void transferOfOrdinaryMemberPreservesAnotherLeader() {
        StudentId other = new StudentId(UUID.randomUUID());
        when(users.findById(other.value())).thenReturn(Optional.of(new UserAccount(other.value(), UserAccount.Role.STUDENT, UserAccount.Status.ACTIVE)));
        memberships.add(ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()), course.getId(), source.getId(), other, now));
        leaders.handle(new DesignateProjectLeaderCommand(source.getId().value(), other.value()), teacher);
        transfers.handle(transferCommand(), teacher);
        assertThat(projects.findById(source.getId()).orElseThrow().getLeaderId()).contains(other);
    }

    @Test
    void rejectsSameGroupCrossCourseAndMissingDestination() {
        assertThatThrownBy(() -> transfers.handle(new TransferProjectMemberCommand(source.getId().value(), source.getId().value(), student.value()), teacher)).isInstanceOf(IllegalArgumentException.class);
        var other = Course.create(new CourseId(UUID.randomUUID()), "Other", "02", "2026-2", new TeacherId(teacher.userId()), now);
        courses.save(other);
        var wrong = createProject(other.getId());
        assertThatThrownBy(() -> transfers.handle(new TransferProjectMemberCommand(source.getId().value(), wrong.getId().value(), student.value()), teacher)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> transfers.handle(new TransferProjectMemberCommand(source.getId().value(), UUID.randomUUID(), student.value()), teacher)).isInstanceOf(ProjectNotFoundException.class);
        assertThat(memberships.findActive(course.getId(), student).orElseThrow().getProjectId()).isEqualTo(source.getId());
    }

    @Test
    void rollsBackClosureAndLeaderChangeWhenDestinationInsertFails() {
        designate();
        var failing = mock(ProjectMembershipRepository.class, delegatesTo(memberships));
        doThrow(new ProjectMembershipConflictException("Simulated insert conflict")).when(failing).add(any());
        var service = new TransferProjectMemberService(projects, failing, enrollments, courses, transactions, users, clock);
        assertThatThrownBy(() -> service.handle(transferCommand(), teacher)).isInstanceOf(ProjectMembershipConflictException.class);
        assertThat(memberships.findActive(course.getId(), student).orElseThrow().getProjectId()).isEqualTo(source.getId());
        assertThat(projects.findById(source.getId()).orElseThrow().getLeaderId()).contains(student);
    }

    @Test
    void rejectsTransferBeforeJoinTime() {
        var early = new TransferProjectMemberService(projects, memberships, enrollments, courses, transactions, users,
                Clock.fixed(now.minusSeconds(75), ZoneOffset.UTC));
        assertThatThrownBy(() -> early.handle(transferCommand(), teacher)).isInstanceOf(IllegalArgumentException.class);
        assertThat(memberships.findActive(course.getId(), student).orElseThrow().getProjectId()).isEqualTo(source.getId());
    }

    @Test
    void concurrentTransfersMoveStudentOnlyOnce() throws Exception {
        designate();
        var third = createProject(course.getId());
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> attempt(transferCommand(), barrier),
                    () -> attempt(new TransferProjectMemberCommand(source.getId().value(), third.getId().value(), student.value()), barrier));
            var results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_memberships WHERE course_id = ? AND student_id = ?", Integer.class, course.getId().value(), student.value())).isEqualTo(2);
        assertThat(projects.findById(source.getId()).orElseThrow().getLeaderId()).isEmpty();
    }

    @Test
    void concurrentDesignationAndTransferCannotLeaveAFormerMemberAsLeader() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    designate();
                    return true;
                } catch (AccessDeniedException expected) {
                    return false;
                }
            }, () -> attempt(transferCommand(), barrier));
            var results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            results.get(0).get();
            assertThat(results.get(1).get()).isTrue();
        }
        assertThat(projects.findById(source.getId()).orElseThrow().getLeaderId()).isEmpty();
        assertThat(memberships.findActive(course.getId(), student).orElseThrow().getProjectId()).isEqualTo(target.getId());
    }

    @Test
    void simultaneousOrganizationRegistrationsCannotOverwriteOneAnother() throws Exception {
        designate();
        var lookupBarrier = new CyclicBarrier(2);
        when(github.findByLogin(anyString())).thenAnswer(call -> {
            String login = call.getArgument(0);
            lookupBarrier.await(10, TimeUnit.SECONDS);
            return Optional.of(new GitHubOrganization(login.equals("first") ? 101 : 102, login));
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> attemptLink("first"), () -> attemptLink("second"));
            var results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(projects.findById(source.getId()).orElseThrow().getGitHubOrganization().orElseThrow().login())
                .isIn("first", "second");
    }

    private boolean attemptLink(String login) {
        try {
            organizations.handle(new LinkGitHubOrganizationCommand(source.getId().value(), login), new Actor(student.value()));
            return true;
        } catch (ProjectConflictException expected) {
            return false;
        }
    }

    @Test
    void closingAnAlreadyClosedMembershipIsAConflict() {
        var previous = memberships.findActive(course.getId(), student).orElseThrow();
        previous.close(now);
        memberships.close(previous);
        assertThatThrownBy(() -> memberships.close(previous)).isInstanceOf(ProjectMembershipConflictException.class);
        assertThat(memberships.findActive(course.getId(), student)).isEmpty();
    }

    private boolean attempt(TransferProjectMemberCommand command, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            transfers.handle(command, teacher);
            return true;
        } catch (ProjectMembershipConflictException expected) {
            return false;
        }
    }
}
