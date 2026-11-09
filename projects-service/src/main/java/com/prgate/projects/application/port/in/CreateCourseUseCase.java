package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.CreateCourseCommand;
import com.prgate.projects.application.result.CreateCourseResult;
import com.prgate.projects.application.security.Actor;

public interface CreateCourseUseCase {

    CreateCourseResult handle(CreateCourseCommand command, Actor actor);
}
