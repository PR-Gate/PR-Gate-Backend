package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.CreateProjectCommand;
import com.prgate.projects.application.result.CreateProjectResult;
import com.prgate.projects.application.security.Actor;

public interface CreateProjectUseCase {

    CreateProjectResult handle(CreateProjectCommand command, Actor actor);
}
