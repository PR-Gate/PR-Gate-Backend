package com.prgate.projects.application.service;

import com.prgate.projects.application.command.RequestCourseEnrollmentCommand;
import com.prgate.projects.application.exception.CourseNotFoundException;
import com.prgate.projects.application.exception.EnrollmentConflictException;
import com.prgate.projects.application.port.in.RequestCourseEnrollmentUseCase;
import com.prgate.projects.application.port.out.CourseEnrollmentRepository;
import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.security.Actor;
import com.prgate.projects.application.security.UserAccessPolicy;
import com.prgate.projects.domain.model.aggregates.CourseEnrollment;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.StudentId;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class RequestCourseEnrollmentService implements RequestCourseEnrollmentUseCase {

    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;
    private final UserAccessPolicy accessPolicy;
    private final Clock clock;

    public RequestCourseEnrollmentService(CourseRepository courses, CourseEnrollmentRepository enrollments,
                                          UserDirectory users, Clock clock) {
        this.courses = Objects.requireNonNull(courses, "Course repository is required");
        this.enrollments = Objects.requireNonNull(enrollments, "Enrollment repository is required");
        this.accessPolicy = new UserAccessPolicy(users);
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    @Override
    public CourseEnrollmentResult handle(RequestCourseEnrollmentCommand command, Actor actor) {
        Objects.requireNonNull(command, "Enrollment request command is required");
        Objects.requireNonNull(actor, "Authenticated actor is required");
        CourseId courseId = new CourseId(command.courseId());
        StudentId studentId = accessPolicy.requireActiveStudent(actor);
        courses.findById(courseId).orElseThrow(() -> new CourseNotFoundException("Course does not exist"));
        if (enrollments.existsPendingOrApproved(courseId, studentId)) {
            throw new EnrollmentConflictException("The student already has a pending or approved enrollment in this course");
        }

        CourseEnrollment enrollment = CourseEnrollment.request(new CourseEnrollmentId(UUID.randomUUID()),
                courseId, studentId, clock.instant());
        enrollments.add(enrollment);
        return CourseEnrollmentResult.from(enrollment);
    }
}
