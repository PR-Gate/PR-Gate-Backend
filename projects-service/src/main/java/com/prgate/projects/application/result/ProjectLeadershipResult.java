package com.prgate.projects.application.result;

import java.util.Optional;
import java.util.UUID;

public record ProjectLeadershipResult(UUID projectId, Optional<UUID> leaderId) { }
