package com.prgate.projects.application.command;

import java.util.UUID;

public record DesignateProjectLeaderCommand(UUID projectId, UUID studentId) { }
