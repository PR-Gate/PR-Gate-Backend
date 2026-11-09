package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.LinkGitHubOrganizationCommand;
import com.prgate.projects.application.result.LinkedOrganizationResult;
import com.prgate.projects.application.security.Actor;

public interface LinkGitHubOrganizationUseCase {
    LinkedOrganizationResult handle(LinkGitHubOrganizationCommand command, Actor actor);
}
