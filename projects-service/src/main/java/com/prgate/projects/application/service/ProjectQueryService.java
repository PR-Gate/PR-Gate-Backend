package com.prgate.projects.application.service;

import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.exception.ProjectNotFoundException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.in.ProjectQueries;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.ProjectReadRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.query.PageRequest;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.result.PageResult;
import com.prgate.projects.application.result.ProjectDetails;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import java.util.Objects;
import java.util.UUID;

public final class ProjectQueryService implements ProjectQueries {
    private final CourseRepository courses;
    private final ProjectRepository projects;
    private final CourseEnrollmentRepository enrollments;
    private final ProjectReadRepository reads;
    private final UserDirectory users;

    public ProjectQueryService(CourseRepository courses, ProjectRepository projects, CourseEnrollmentRepository enrollments,
                               ProjectReadRepository reads, UserDirectory users) {
        this.courses = Objects.requireNonNull(courses);
        this.projects = Objects.requireNonNull(projects);
        this.enrollments = Objects.requireNonNull(enrollments);
        this.reads = Objects.requireNonNull(reads);
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public PageResult<CreateCourseResult> courses(Actor actor, PageRequest page) {
        return reads.courses(activeUser(actor), page);
    }
    @Override
    public PageResult<CreateCourseResult> catalog(Actor actor, PageRequest page) {
        if (activeUser(actor).role() != UserAccount.Role.STUDENT) {
            throw new AccessDeniedException("Course catalog is for active students");
        }
        return reads.catalog(page);
    }
    @Override
    public CreateCourseResult course(UUID id, Actor actor) {
        Course course = requireCourseAccess(id, activeUser(actor));
        return new CreateCourseResult(id, course.getName(), course.getSection(), course.getAcademicPeriod(),
                course.getTeacherId().value(), course.getCreatedAt());
    }
    @Override
    public PageResult<ProjectDetails> projects(UUID courseId, Actor actor, PageRequest page) {
        requireCourseAccess(courseId, activeUser(actor));
        return reads.projects(courseId, page);
    }
    @Override
    public ProjectDetails project(UUID id, Actor actor) {
        var user = activeUser(actor);
        var project = projects.findById(new ProjectId(id)).orElseThrow(() -> new ProjectNotFoundException("Project does not exist"));
        requireCourseAccess(project.getCourseId().value(), user);
        var org = project.getGitHubOrganization();
        return new ProjectDetails(id, project.getCourseId().value(), project.getName(), project.getDescription(),
                project.getLeaderId().map(StudentId::value).orElse(null), org.map(o -> o.id()).orElse(null),
                org.map(o -> o.login()).orElse(null), project.getCreatedAt());
    }
    @Override
    public PageResult<CourseEnrollmentResult> enrollments(UUID courseId, Actor actor, PageRequest page) {
        var user = activeUser(actor);
        if (user.role() != UserAccount.Role.TEACHER) {
            throw new AccessDeniedException("Only the responsible teacher can list enrollment requests");
        }
        requireCourseAccess(courseId, user);
        return reads.enrollments(courseId, page);
    }
    @Override
    public PageResult<CourseEnrollmentResult> ownEnrollments(Actor actor, PageRequest page) {
        var user = activeUser(actor);
        if (user.role() != UserAccount.Role.STUDENT) {
            throw new AccessDeniedException("Only students can list their enrollment history");
        }
        return reads.ownEnrollments(user.id(), page);
    }
    @Override
    public PageResult<CourseEnrollmentResult> students(UUID courseId, Actor actor, PageRequest page) {
        requireCourseAccess(courseId, activeUser(actor));
        return reads.students(courseId, page);
    }
    @Override
    public PageResult<ProjectMembershipResult> members(UUID projectId, Actor actor, PageRequest page) {
        project(projectId, actor);
        return reads.members(projectId, page);
    }

    private UserAccount activeUser(Actor actor) {
        Objects.requireNonNull(actor);
        var user = users.findById(actor.userId()).orElseThrow(() -> new UserNotFoundException("User does not exist"));
        if (!user.id().equals(actor.userId())) {
            throw new UserDirectoryException("Users returned a different identity");
        }
        if (user.status() != UserAccount.Status.ACTIVE || user.role() == UserAccount.Role.ADMIN) {
            throw new AccessDeniedException("An active teacher or student is required");
        }
        return user;
    }
    private Course requireCourseAccess(UUID id, UserAccount user) {
        var courseId = new CourseId(id);
        var course = courses.findById(courseId).orElseThrow(() -> new CourseNotFoundException("Course does not exist"));
        boolean allowed = user.role() == UserAccount.Role.TEACHER
                ? course.getTeacherId().value().equals(user.id())
                : enrollments.findApproved(courseId, new StudentId(user.id())).isPresent();
        if (!allowed) {
            throw new AccessDeniedException("Access to this course is not allowed");
        }
        return course;
    }
}
