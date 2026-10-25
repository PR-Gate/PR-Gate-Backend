package com.prgate.projects.infrastructure;

import com.prgate.projects.application.command.CreateProjectCommand;
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
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import com.prgate.projects.infrastructure.persistence.adapter.JpaCourseRepositoryAdapter;
import com.prgate.projects.infrastructure.persistence.adapter.JpaProjectRepositoryAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaCourseRepositoryAdapter.class, JpaProjectRepositoryAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class JpaProjectRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-10-25T05:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private CourseRepository courses;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAndLoadsProjectWithoutLeaderOrOrganization() {
        Project project = projectFor(savedCourse());

        projects.save(project);

        Project loaded = projects.findById(project.getId()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(project);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM projects WHERE id = ?", project.getId().value());
        assertThat(row.get("course_id")).isEqualTo(project.getCourseId().value());
        assertThat(row.get("name")).isEqualTo("Grupo 1");
        assertThat(row.get("description")).isEqualTo("Proyecto académico");
        assertThat(row.get("leader_id")).isNull();
        assertThat(row.get("github_org_id")).isNull();
        assertThat(row.get("github_org_login")).isNull();
    }

    @Test
    void preservesLeaderAndOrganizationMetadata() {
        Project project = projectFor(savedCourse());
        StudentId leaderId = new StudentId(UUID.randomUUID());
        GitHubOrganization organization = new GitHubOrganization(123456789L, "PRGate-Team");
        project.designateLeader(leaderId);
        project.linkGitHubOrganization(organization, leaderId);

        projects.save(project);

        Project loaded = projects.findById(project.getId()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(project);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM projects WHERE id = ?", project.getId().value());
        assertThat(row.get("leader_id")).isEqualTo(leaderId.value());
        assertThat(row.get("github_org_id")).isEqualTo(123456789L);
        assertThat(row.get("github_org_login")).isEqualTo("prgate-team");
    }

    @Test
    void preservesOrganizationAfterLeaderIsRemoved() {
        Project project = projectFor(savedCourse());
        StudentId leaderId = new StudentId(UUID.randomUUID());
        GitHubOrganization organization = new GitHubOrganization(123L, "prgate-team");
        project.designateLeader(leaderId);
        project.linkGitHubOrganization(organization, leaderId);
        projects.save(project);
        Project loaded = projects.findById(project.getId()).orElseThrow();

        loaded.removeLeader();
        projects.save(loaded);

        Project reloaded = projects.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getLeaderId()).isEmpty();
        assertThat(reloaded.getGitHubOrganization()).contains(organization);
        assertThat(reloaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsProjectWhoseCourseDoesNotExist() {
        Project project = Project.create(new ProjectId(UUID.randomUUID()), "Grupo 1", "",
                new CourseId(UUID.randomUUID()), NOW);

        assertThatThrownBy(() -> projects.save(project))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_projects_course");

        assertThat(projects.findById(project.getId())).isEmpty();
    }

    @Test
    void rejectsIncompleteOrganizationMetadata() {
        UUID courseId = savedCourse().getId().value();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO projects (id, course_id, name, description, created_at, github_org_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), courseId, "Grupo 1", "", Timestamp.from(NOW), 123L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_projects_github_org");
    }

    @Test
    void rejectsNonPositiveOrganizationId() {
        UUID courseId = savedCourse().getId().value();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO projects (id, course_id, name, description, created_at, github_org_id, github_org_login) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), courseId, "Grupo 1", "", Timestamp.from(NOW), -1L, "prgate-team"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_projects_github_org");
    }

    @Test
    void returnsEmptyForUnknownProject() {
        assertThat(projects.findById(new ProjectId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void createProjectUseCasePersistsWithRealRepositoriesAndSimulatedUsers() {
        Course course = savedCourse();
        UserDirectory users = id -> Optional.of(new UserAccount(id, UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE));
        CreateProjectService service = new CreateProjectService(courses, projects, users, Clock.fixed(NOW, ZoneOffset.UTC));

        CreateProjectResult result = service.handle(new CreateProjectCommand(course.getId().value(), "Grupo 2", ""),
                new Actor(course.getTeacherId().value()));

        Project saved = projects.findById(new ProjectId(result.id())).orElseThrow();
        assertThat(saved.getCourseId()).isEqualTo(course.getId());
        assertThat(saved.getName()).isEqualTo("Grupo 2");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void upgradesV1DatabaseWithoutLosingExistingCourses() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway initial = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).target("1").load();
        initial.migrate();
        UUID courseId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".courses (id, name, section, academic_period, teacher_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                courseId, "Aplicaciones Web", "1234", "2026-2", UUID.randomUUID(), Timestamp.from(NOW));

        Flyway upgraded = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).load();

        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT name FROM " + schema + ".courses WHERE id = ?", String.class, courseId))
                .isEqualTo("Aplicaciones Web");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".projects", Integer.class)).isZero();
    }

    private Course savedCourse() {
        Course course = Course.create(new CourseId(UUID.randomUUID()), "Aplicaciones Web", "1234", "2026-2",
                new TeacherId(UUID.randomUUID()), NOW.minusSeconds(60));
        courses.save(course);
        return course;
    }

    private static Project projectFor(Course course) {
        return Project.create(new ProjectId(UUID.randomUUID()), "Grupo 1", "Proyecto académico", course.getId(), NOW);
    }
}
