package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.JoinProjectCommand;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;

public interface JoinProjectUseCase {

    ProjectMembershipResult handle(JoinProjectCommand command, Actor actor);
}
