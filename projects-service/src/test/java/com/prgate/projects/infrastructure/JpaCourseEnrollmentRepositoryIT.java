package com.prgate.projects.infrastructure;

import com.prgate.projects.application.command.RequestCourseEnrollmentCommand;
import com.prgate.projects.application.command.ResolveCourseEnrollmentCommand;
import com.prgate.projects.application.exception.EnrollmentConflictException;
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
import com.prgate.projects.infrastructure.persistence.adapter.JpaCourseEnrollmentRepositoryAdapter;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.datasource.hikari.connection-init-sql=SET statement_timeout TO '10s'")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaCourseRepositoryAdapter.class, JpaCourseEnrollmentRepositoryAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class JpaCourseEnrollmentRepositoryIT {

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
    private CourseEnrollmentRepository enrollments;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesAndLoadsPendingRequestWithItsOriginalFields() {
        Course course = savedCourse();
        CourseEnrollment request = pending(course, new StudentId(UUID.randomUUID()));

        enrollments.add(request);

        assertThat(enrollments.findById(request.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(request);
        assertThat(enrollments.existsPendingOrApproved(course.getId(), request.getStudentId())).isTrue();
    }

    @Test
    void approvedLookupFiltersStateCourseAndStudentWhilePreservingHistory() {
        Course course = savedCourse();
        StudentId student = new StudentId(UUID.randomUUID());
        assertThat(enrollments.findApproved(course.getId(), student)).isEmpty();
        CourseEnrollment rejected = pending(course, student);
        enrollments.add(rejected);
        assertThat(enrollments.findApproved(course.getId(), student)).isEmpty();
        rejected.reject(NOW.plusSeconds(60));
        enrollments.resolve(rejected);
        assertThat(enrollments.findApproved(course.getId(), student)).isEmpty();
        CourseEnrollment approved = CourseEnrollment.request(new CourseEnrollmentId(UUID.randomUUID()),
                course.getId(), student, NOW.plusSeconds(120));
        enrollments.add(approved);
        approved.approve(NOW.plusSeconds(180));
        enrollments.resolve(approved);

        CourseEnrollment found = enrollments.findApproved(course.getId(), student).orElseThrow();

        assertThat(found).usingRecursiveComparison().isEqualTo(approved);
        assertThat(enrollments.findApproved(new CourseId(UUID.randomUUID()), student)).isEmpty();
        assertThat(enrollments.findApproved(course.getId(), new StudentId(UUID.randomUUID()))).isEmpty();
        assertThat(enrollments.findById(rejected.getId()).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.REJECTED);
    }

    @Test
    void returnsEmptyAndFalseWhenNoRequestExists() {
        Course course = savedCourse();

        assertThat(enrollments.findById(new CourseEnrollmentId(UUID.randomUUID()))).isEmpty();
        assertThat(enrollments.existsPendingOrApproved(course.getId(), new StudentId(UUID.randomUUID()))).isFalse();
    }

    @Test
    void cannotAddRequestForMissingCourse() {
        CourseEnrollment request = CourseEnrollment.request(new CourseEnrollmentId(UUID.randomUUID()),
                new CourseId(UUID.randomUUID()), new StudentId(UUID.randomUUID()), NOW);

        assertThatThrownBy(() -> enrollments.add(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_course_enrollments_course");
    }

    @Test
    void approvedEnrollmentAlsoBlocksNewPendingRequest() {
        Course course = savedCourse();
        StudentId student = new StudentId(UUID.randomUUID());
        CourseEnrollment first = pending(course, student);
        enrollments.add(first);
        first.approve(NOW.plusSeconds(60));
        enrollments.resolve(first);

        assertThatThrownBy(() -> enrollments.add(pending(course, student)))
                .isInstanceOf(EnrollmentConflictException.class);

        assertThat(enrollments.existsPendingOrApproved(course.getId(), student)).isTrue();
        assertThat(enrollments.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void sameStudentMayRequestAnotherCourse() {
        Course firstCourse = savedCourse();
        Course secondCourse = savedCourse();
        StudentId student = new StudentId(UUID.randomUUID());

        enrollments.add(pending(firstCourse, student));
        enrollments.add(pending(secondCourse, student));

        assertThat(enrollments.existsPendingOrApproved(firstCourse.getId(), student)).isTrue();
        assertThat(enrollments.existsPendingOrApproved(secondCourse.getId(), student)).isTrue();
    }

    @Test
    void cannotReuseHistoricalRequestIdentity() {
        Course course = savedCourse();
        CourseEnrollment first = pending(course, new StudentId(UUID.randomUUID()));
        enrollments.add(first);
        first.reject(NOW.plusSeconds(60));
        enrollments.resolve(first);
        CourseEnrollment reused = CourseEnrollment.request(first.getId(), course.getId(), first.getStudentId(), NOW.plusSeconds(120));

        assertThatThrownBy(() -> enrollments.add(reused)).isInstanceOf(EnrollmentConflictException.class);

        assertThat(enrollments.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.REJECTED);
    }

    @Test
    void onlyOneConcurrentRequestForCourseAndStudentSucceeds() throws Exception {
        Course course = savedCourse();
        StudentId student = new StudentId(UUID.randomUUID());
        CourseEnrollment first = pending(course, student);
        CourseEnrollment second = pending(course, student);

        List<Boolean> outcomes = race(() -> enrollments.add(first), () -> enrollments.add(second));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_enrollments WHERE course_id = ? AND student_id = ?",
                Integer.class, course.getId().value(), student.value())).isEqualTo(1);
    }

    @Test
    void onlyOneConcurrentDecisionCanChangePendingRequest() throws Exception {
        CourseEnrollment request = pending(savedCourse(), new StudentId(UUID.randomUUID()));
        enrollments.add(request);
        CourseEnrollment approval = enrollments.findById(request.getId()).orElseThrow();
        CourseEnrollment rejection = enrollments.findById(request.getId()).orElseThrow();
        approval.approve(NOW.plusSeconds(60));
        rejection.reject(NOW.plusSeconds(120));

        List<Boolean> outcomes = race(() -> enrollments.resolve(approval), () -> enrollments.resolve(rejection));

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        CourseEnrollment stored = enrollments.findById(request.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(outcomes.getFirst() ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED);
        assertThat(stored.getResolvedAt()).contains(outcomes.getFirst() ? NOW.plusSeconds(60) : NOW.plusSeconds(120));
    }

    @Test
    void cannotResolveMissingRequest() {
        CourseEnrollment request = pending(savedCourse(), new StudentId(UUID.randomUUID()));
        request.approve(NOW.plusSeconds(60));

        assertThatThrownBy(() -> enrollments.resolve(request)).isInstanceOf(EnrollmentConflictException.class);
    }

    @Test
    void cannotChangeImmutableRequestReferencesDuringResolution() {
        CourseEnrollment request = pending(savedCourse(), new StudentId(UUID.randomUUID()));
        enrollments.add(request);
        CourseEnrollment mismatched = CourseEnrollment.reconstitute(request.getId(), request.getCourseId(),
                new StudentId(UUID.randomUUID()), request.getRequestedAt(), EnrollmentStatus.APPROVED, NOW.plusSeconds(60));

        assertThatThrownBy(() -> enrollments.resolve(mismatched)).isInstanceOf(EnrollmentConflictException.class);

        assertThat(enrollments.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    void insertAndResolutionRequireCorrectLifecycleState() {
        CourseEnrollment request = pending(savedCourse(), new StudentId(UUID.randomUUID()));

        assertThatThrownBy(() -> enrollments.resolve(request)).isInstanceOf(IllegalArgumentException.class);
        request.reject(NOW.plusSeconds(60));
        assertThatThrownBy(() -> enrollments.add(request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(enrollments.findById(request.getId())).isEmpty();
    }

    @Test
    void databaseRejectsFinalStateWithoutResolutionTime() {
        Course course = savedCourse();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO course_enrollments (id, course_id, student_id, status, requested_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), course.getId().value(), UUID.randomUUID(), "APPROVED", Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_course_enrollments_resolution");
    }

    @Test
    void rejectedStudentCanRequestAgainWithoutErasingHistory() {
        Course course = savedCourse();
        StudentId student = new StudentId(UUID.randomUUID());
        UserDirectory users = id -> Optional.of(new UserAccount(id,
                id.equals(student.value()) ? UserAccount.Role.STUDENT : UserAccount.Role.TEACHER, UserAccount.Status.ACTIVE));
        RequestCourseEnrollmentService requests = new RequestCourseEnrollmentService(courses, enrollments, users,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ResolveCourseEnrollmentService decisions = new ResolveCourseEnrollmentService(courses, enrollments, users,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        RequestCourseEnrollmentCommand command = new RequestCourseEnrollmentCommand(course.getId().value());
        CourseEnrollmentResult first = requests.handle(command, new Actor(student.value()));
        decisions.handle(new ResolveCourseEnrollmentCommand(first.id(), EnrollmentDecision.REJECT),
                new Actor(course.getTeacherId().value()));
        RequestCourseEnrollmentService retryRequests = new RequestCourseEnrollmentService(courses, enrollments, users,
                Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));

        CourseEnrollmentResult second = retryRequests.handle(command, new Actor(student.value()));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(EnrollmentStatus.PENDING);
        CourseEnrollment historical = enrollments.findById(new CourseEnrollmentId(first.id())).orElseThrow();
        assertThat(historical.getStatus()).isEqualTo(EnrollmentStatus.REJECTED);
        assertThat(historical.getResolvedAt()).contains(NOW.plusSeconds(60));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_enrollments WHERE course_id = ? AND student_id = ?",
                Integer.class, course.getId().value(), student.value())).isEqualTo(2);
    }

    @Test
    void migratesV2WithoutLosingExistingCourseAndProject() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway initial = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).target("2").load();
        initial.migrate();
        UUID courseId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".courses (id, name, section, academic_period, teacher_id, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                courseId, "Web", "1234", "2026-2", UUID.randomUUID(), Timestamp.from(NOW));
        jdbc.update("INSERT INTO " + schema + ".projects (id, course_id, name, description, created_at) VALUES (?, ?, ?, ?, ?)",
                projectId, courseId, "Grupo 1", "", Timestamp.from(NOW));
        Flyway upgraded = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).load();

        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM " + schema + ".courses WHERE id = ?", String.class, courseId)).isEqualTo("Web");
        assertThat(jdbc.queryForObject("SELECT name FROM " + schema + ".projects WHERE id = ?", String.class, projectId)).isEqualTo("Grupo 1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".course_enrollments", Integer.class)).isZero();
    }

    private Course savedCourse() {
        Course course = Course.create(new CourseId(UUID.randomUUID()), "Web", "1234", "2026-2",
                new TeacherId(UUID.randomUUID()), NOW.minusSeconds(60));
        courses.save(course);
        return course;
    }

    private static CourseEnrollment pending(Course course, StudentId student) {
        return CourseEnrollment.request(new CourseEnrollmentId(UUID.randomUUID()), course.getId(), student, NOW);
    }

    private static List<Boolean> race(Runnable first, Runnable second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> tasks = List.of(() -> attempt(start, first), () -> attempt(start, second));
            List<Future<Boolean>> results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            return List.of(results.get(0).get(), results.get(1).get());
        }
    }

    private static boolean attempt(CyclicBarrier start, Runnable action) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            action.run();
            return true;
        } catch (EnrollmentConflictException conflict) {
            return false;
        }
    }
}
