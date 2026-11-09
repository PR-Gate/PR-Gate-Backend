package com.prgate.projects.infrastructure;

import com.prgate.projects.application.port.out.ProjectMembershipRepository;
import com.prgate.projects.application.exception.ProjectMembershipConflictException;
import com.prgate.projects.domain.model.aggregates.ProjectMembership;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.infrastructure.persistence.adapter.JpaProjectMembershipRepositoryAdapter;
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
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaProjectMembershipRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class JpaProjectMembershipRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ProjectMembershipRepository memberships;
    @Autowired private JdbcTemplate jdbc;
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    private UUID project(UUID course) {
        jdbc.update("INSERT INTO courses (id, name, section, academic_period, teacher_id, created_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                course, "Software", "01", "2026-2", UUID.randomUUID(), Timestamp.from(NOW));
        UUID project = UUID.randomUUID();
        jdbc.update("INSERT INTO projects (id, course_id, name, description, created_at) VALUES (?, ?, ?, ?, ?)",
                project, course, "Group", "", Timestamp.from(NOW));
        return project;
    }

    private ProjectMembership membership(UUID course, UUID project, UUID student) {
        return ProjectMembership.join(new ProjectMembershipId(UUID.randomUUID()), new CourseId(course),
                new ProjectId(project), new StudentId(student), NOW);
    }

    @Test
    void commitsAndLoadsActiveMembership() {
        UUID course = UUID.randomUUID(), student = UUID.randomUUID();
        ProjectMembership value = membership(course, project(course), student);
        memberships.add(value);
        assertThat(memberships.findActive(new CourseId(course), new StudentId(student)).orElseThrow())
                .usingRecursiveComparison().isEqualTo(value);
        assertThat(memberships.findActive(new CourseId(course), new StudentId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void preservesHistoryAndNeverReusesAnExistingIdentity() {
        UUID course = UUID.randomUUID(), student = UUID.randomUUID();
        ProjectMembership old = membership(course, project(course), student);
        memberships.add(old);
        jdbc.update("UPDATE project_memberships SET left_at = joined_at WHERE id = ?", old.getId().value());
        assertThat(memberships.findActive(new CourseId(course), new StudentId(student))).isEmpty();
        assertThatThrownBy(() -> memberships.add(old)).isInstanceOf(ProjectMembershipConflictException.class);
        ProjectMembership next = membership(course, project(course), student);
        memberships.add(next);
        assertThat(memberships.findActive(new CourseId(course), new StudentId(student)).orElseThrow().getId()).isEqualTo(next.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_memberships WHERE student_id = ?", Integer.class, student)).isEqualTo(2);
    }

    @Test
    void permitsDifferentCourses() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), student = UUID.randomUUID();
        memberships.add(membership(a, project(a), student));
        memberships.add(membership(b, project(b), student));
    }

    @Test
    void rejectsMismatchedCourseWithoutTranslatingForeignKeyErrorToConflict() {
        UUID course = UUID.randomUUID();
        project(course);
        UUID wrong = project(UUID.randomUUID());
        assertThatThrownBy(() -> memberships.add(membership(course, wrong, UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClosedAggregateAndInvalidDates() {
        UUID course = UUID.randomUUID();
        ProjectMembership value = membership(course, project(course), UUID.randomUUID());
        value.close(NOW);
        assertThatThrownBy(() -> memberships.add(value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProjectMembership.reconstitute(value.getId(), value.getCourseId(),
                value.getProjectId(), value.getStudentId(), NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        ProjectMembership restored = ProjectMembership.reconstitute(value.getId(), value.getCourseId(),
                value.getProjectId(), value.getStudentId(), NOW, NOW);
        assertThat(restored.isActive()).isFalse();
        memberships.add(membership(course, value.getProjectId().value(), value.getStudentId().value()));
        assertThatThrownBy(() -> jdbc.update("UPDATE project_memberships SET left_at = ? WHERE student_id = ?",
                Timestamp.from(NOW.minusSeconds(1)), value.getStudentId().value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void simultaneousJoinsCommitExactlyOneMembership() throws Exception {
        UUID course = UUID.randomUUID(), student = UUID.randomUUID();
        ProjectMembership a = membership(course, project(course), student);
        ProjectMembership b = membership(course, project(course), student);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> attempt(a, barrier), () -> attempt(b, barrier));
            var results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_memberships WHERE course_id = ? AND student_id = ? AND left_at IS NULL",
                Integer.class, course, student)).isEqualTo(1);
    }

    private boolean attempt(ProjectMembership value, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            memberships.add(value);
            return true;
        } catch (ProjectMembershipConflictException expected) {
            return false;
        }
    }
}
