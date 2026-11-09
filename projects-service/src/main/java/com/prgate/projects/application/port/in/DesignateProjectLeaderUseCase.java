package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.DesignateProjectLeaderCommand;
import com.prgate.projects.application.result.ProjectLeadershipResult;
import com.prgate.projects.application.security.Actor;

public interface DesignateProjectLeaderUseCase {
    ProjectLeadershipResult handle(DesignateProjectLeaderCommand command, Actor actor);
}
