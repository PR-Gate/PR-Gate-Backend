package com.prgate.projects.application.command;

import java.util.UUID;

public record TransferProjectMemberCommand(UUID sourceProjectId, UUID targetProjectId, UUID studentId) { }
