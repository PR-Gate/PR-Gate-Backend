package com.prgate.projects.application.service;

import com.prgate.projects.application.command.CreateProjectCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.port.in.CreateProjectUseCase;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.ProjectRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CreateProjectResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.TeacherId;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class CreateProjectService implements CreateProjectUseCase {

    private final CourseRepository courses;
    private final ProjectRepository projects;
    private final UserAccessPolicy accessPolicy;
    private final Clock clock;

    public CreateProjectService(CourseRepository courses, ProjectRepository projects, UserDirectory users, Clock clock) {
        this.courses = Objects.requireNonNull(courses, "Course repository is required");
        this.projects = Objects.requireNonNull(projects, "Project repository is required");
        this.accessPolicy = new UserAccessPolicy(users);
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public CreateProjectResult handle(CreateProjectCommand command, Actor actor) {
        Objects.requireNonNull(command, "Create project command is required");
        Objects.requireNonNull(actor, "Authenticated actor is required");
        CourseId courseId = new CourseId(command.courseId());
        TeacherId teacherId = accessPolicy.requireActiveTeacher(actor);
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Course does not exist"));
        if (!course.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("Only the responsible teacher can create projects in this course");
        }

        Project project = Project.create(new ProjectId(UUID.randomUUID()), command.name(), command.description(),
                course.getId(), clock.instant());
        projects.save(project);

        return new CreateProjectResult(project.getId().value(), project.getCourseId().value(), project.getName(),
                project.getDescription(), project.getCreatedAt());
    }
}
