package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.ProjectReadRepository;
import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.application.result.ProjectDetails;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.domain.model.enums.EnrollmentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class JdbcProjectReadRepository implements ProjectReadRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<CreateCourseResult> COURSE = (r, n) -> new CreateCourseResult(
            r.getObject("id", UUID.class), r.getString("name"), r.getString("section"), r.getString("academic_period"),
            r.getObject("teacher_id", UUID.class), r.getTimestamp("created_at").toInstant());
    private static final RowMapper<CourseEnrollmentResult> ENROLLMENT = (r, n) -> new CourseEnrollmentResult(
            r.getObject("id", UUID.class), r.getObject("course_id", UUID.class), r.getObject("student_id", UUID.class),
            EnrollmentStatus.valueOf(r.getString("status")), r.getTimestamp("requested_at").toInstant(),
            Optional.ofNullable(r.getTimestamp("resolved_at")).map(t -> t.toInstant()));
    public JdbcProjectReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PageResult<CreateCourseResult> courses(UserAccount user, PageRequest page) {
        String filter = user.role() == UserAccount.Role.TEACHER ? "c.teacher_id = ?"
                : "EXISTS (SELECT 1 FROM course_enrollments e WHERE e.course_id=c.id AND e.student_id=? AND e.status='APPROVED')";
        return PageResult.from(jdbc.query("SELECT c.* FROM courses c WHERE " + filter + " ORDER BY c.created_at,c.id LIMIT ? OFFSET ?",
                COURSE, user.id(), page.size()+1, page.offset()), page);
    }
    @Override
    public PageResult<CreateCourseResult> catalog(PageRequest page) {
        return PageResult.from(jdbc.query("SELECT * FROM courses ORDER BY created_at,id LIMIT ? OFFSET ?",
                COURSE, page.size()+1, page.offset()), page);
    }
    @Override
    public PageResult<ProjectDetails> projects(UUID courseId, PageRequest page) {
        return PageResult.from(jdbc.query("SELECT * FROM projects WHERE course_id=? ORDER BY created_at,id LIMIT ? OFFSET ?", (r, n) ->
                new ProjectDetails(r.getObject("id", UUID.class), courseId, r.getString("name"), r.getString("description"),
                        r.getObject("leader_id", UUID.class), r.getObject("github_org_id", Long.class),
                        r.getString("github_org_login"), r.getTimestamp("created_at").toInstant()), courseId, page.size()+1, page.offset()), page);
    }
    @Override
    public PageResult<CourseEnrollmentResult> enrollments(UUID courseId, PageRequest page) {
        return enrollmentPage("course_id=?", courseId, page);
    }
    @Override
    public PageResult<CourseEnrollmentResult> students(UUID courseId, PageRequest page) {
        return enrollmentPage("course_id=? AND status='APPROVED'", courseId, page);
    }
    @Override
    public PageResult<CourseEnrollmentResult> ownEnrollments(UUID studentId, PageRequest page) {
        return enrollmentPage("student_id=?", studentId, page);
    }
    private PageResult<CourseEnrollmentResult> enrollmentPage(String fixedFilter, UUID id, PageRequest page) {
        return PageResult.from(jdbc.query("SELECT * FROM course_enrollments WHERE " + fixedFilter
                + " ORDER BY requested_at,id LIMIT ? OFFSET ?", ENROLLMENT, id, page.size()+1, page.offset()), page);
    }
    @Override
    public PageResult<ProjectMembershipResult> members(UUID projectId, PageRequest page) {
        return PageResult.from(jdbc.query("SELECT * FROM project_memberships WHERE project_id=? AND left_at IS NULL ORDER BY joined_at,id LIMIT ? OFFSET ?", (r, n) ->
                new ProjectMembershipResult(r.getObject("id", UUID.class), r.getObject("course_id", UUID.class), projectId,
                        r.getObject("student_id", UUID.class), r.getTimestamp("joined_at").toInstant(), Optional.empty()),
                projectId, page.size()+1, page.offset()), page);
    }
}
