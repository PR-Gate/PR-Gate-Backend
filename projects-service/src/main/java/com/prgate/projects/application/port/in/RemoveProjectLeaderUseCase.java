package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.RemoveProjectLeaderCommand;
import com.prgate.projects.application.result.ProjectLeadershipResult;
import com.prgate.projects.application.security.Actor;

public interface RemoveProjectLeaderUseCase {
    ProjectLeadershipResult handle(RemoveProjectLeaderCommand command, Actor actor);
}
