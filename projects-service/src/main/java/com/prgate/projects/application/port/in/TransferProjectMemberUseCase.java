package com.prgate.projects.application.port.in;

import com.prgate.projects.application.command.TransferProjectMemberCommand;
import com.prgate.projects.application.result.ProjectMembershipResult;
import com.prgate.projects.application.security.Actor;

public interface TransferProjectMemberUseCase {
    ProjectMembershipResult handle(TransferProjectMemberCommand command, Actor actor);
}
