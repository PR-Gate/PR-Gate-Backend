package com.prgate.projects.application.service;

import com.prgate.projects.application.command.ResolveCourseEnrollmentCommand;
import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.exception.EnrollmentNotFoundException;
import com.prgate.projects.application.port.in.ResolveCourseEnrollmentUseCase;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.TeacherId;

import java.time.Clock;
import java.util.Objects;

public final class ResolveCourseEnrollmentService implements ResolveCourseEnrollmentUseCase {

    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;
    private final UserAccessPolicy accessPolicy;
    private final Clock clock;

    public ResolveCourseEnrollmentService(CourseRepository courses, CourseEnrollmentRepository enrollments,
                                          UserDirectory users, Clock clock) {
        this.courses = Objects.requireNonNull(courses, "Course repository is required");
        this.enrollments = Objects.requireNonNull(enrollments, "Enrollment repository is required");
        this.accessPolicy = new UserAccessPolicy(users);
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public CourseEnrollmentResult handle(ResolveCourseEnrollmentCommand command, Actor actor) {
        Objects.requireNonNull(command, "Enrollment resolution command is required");
        Objects.requireNonNull(actor, "Authenticated actor is required");
        Objects.requireNonNull(command.decision(), "Enrollment decision is required");
        CourseEnrollmentId enrollmentId = new CourseEnrollmentId(command.enrollmentId());
        TeacherId teacherId = accessPolicy.requireActiveTeacher(actor);
        CourseEnrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment request does not exist"));
        Course course = courses.findById(enrollment.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException("Course does not exist"));
        if (!course.getTeacherId().equals(teacherId)) {
            throw new AccessDeniedException("Only the responsible teacher can resolve enrollment requests in this course");
        }

        switch (command.decision()) {
            case APPROVE -> enrollment.approve(clock.instant());
            case REJECT -> enrollment.reject(clock.instant());
        }
        enrollments.resolve(enrollment);
        return CourseEnrollmentResult.from(enrollment);
    }
}
