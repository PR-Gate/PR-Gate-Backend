package com.prgate.projects.infrastructure;

import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import com.prgate.projects.infrastructure.persistence.adapter.JpaCourseRepositoryAdapter;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaCourseRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class JpaCourseRepositoryIT {

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
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    void migratesEmptyDatabaseAndDoesNotRepeatAppliedMigration() {
        flyway.validate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '3' AND success", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void savesAndCommitsCourseWithAllDomainFields() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-24T15:00:00Z");
        Course course = Course.create(new CourseId(courseId), "Aplicaciones Web", "1234", "2026-2",
                new TeacherId(teacherId), createdAt);

        courses.save(course);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM courses WHERE id = ?", courseId);
        assertThat(row.get("id")).isEqualTo(courseId);
        assertThat(row.get("name")).isEqualTo("Aplicaciones Web");
        assertThat(row.get("section")).isEqualTo("1234");
        assertThat(row.get("academic_period")).isEqualTo("2026-2");
        assertThat(row.get("teacher_id")).isEqualTo(teacherId);
        assertThat(jdbc.queryForObject("SELECT created_at FROM courses WHERE id = ?", Timestamp.class, courseId)
                .toInstant()).isEqualTo(createdAt);
    }

    @Test
    void loadsPersistedCourseAsDomainAggregate() {
        Course course = Course.create(new CourseId(UUID.randomUUID()), "Aplicaciones Web", "1234", "2026-2",
                new TeacherId(UUID.randomUUID()), Instant.parse("2026-10-25T05:00:00Z"));
        courses.save(course);

        Course loaded = courses.findById(course.getId()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(course);
    }

    @Test
    void returnsEmptyForUnknownCourse() {
        assertThat(courses.findById(new CourseId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void databaseRejectsBlankNameEvenOutsideDomainFactory() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO courses (id, name, section, academic_period, teacher_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), " ", "1234", "2026-2", UUID.randomUUID(), Timestamp.from(Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_courses_name");
    }
}
