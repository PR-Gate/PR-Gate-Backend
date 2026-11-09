package com.prgate.projects.application.result;

import java.util.UUID;

public record LinkedOrganizationResult(UUID projectId, long organizationId, String login) { }
