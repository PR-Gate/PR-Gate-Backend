package com.prgate.projects.application.service;

import com.prgate.projects.application.command.CreateCourseCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.in.CreateCourseUseCase;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.TeacherId;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class CreateCourseService implements CreateCourseUseCase {

    private final CourseRepository courses;
    private final UserDirectory users;
    private final Clock clock;

    public CreateCourseService(CourseRepository courses, UserDirectory users, Clock clock) {
        this.courses = Objects.requireNonNull(courses, "Course repository is required");
        this.users = Objects.requireNonNull(users, "User directory is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public CreateCourseResult handle(CreateCourseCommand command, Actor actor) {
        Objects.requireNonNull(command, "Create course command is required");
        Objects.requireNonNull(actor, "Authenticated actor is required");

        UserAccount user = users.findById(actor.userId())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user does not exist"));
        if (!user.id().equals(actor.userId())) {
            throw new UserDirectoryException("Users returned an identity different from the requested user");
        }
        if (user.status() != UserAccount.Status.ACTIVE || user.role() != UserAccount.Role.TEACHER) {
            throw new AccessDeniedException("Only an active teacher can create a course");
        }

        Course course = Course.create(new CourseId(UUID.randomUUID()), command.name(), command.section(),
                command.academicPeriod(), new TeacherId(actor.userId()), clock.instant());
        courses.save(course);

        return new CreateCourseResult(course.getId().value(), course.getName(), course.getSection(),
                course.getAcademicPeriod(), course.getTeacherId().value(), course.getCreatedAt());
    }
}
