package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.RequestCourseEnrollmentCommand;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.security.Actor;

public interface RequestCourseEnrollmentUseCase {

    CourseEnrollmentResult handle(RequestCourseEnrollmentCommand command, Actor actor);
}
