package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.ResolveCourseEnrollmentCommand;
import com.prgate.projects.application.result.CourseEnrollmentResult;
import com.prgate.projects.application.security.Actor;

public interface ResolveCourseEnrollmentUseCase {

    CourseEnrollmentResult handle(ResolveCourseEnrollmentCommand command, Actor actor);
}
