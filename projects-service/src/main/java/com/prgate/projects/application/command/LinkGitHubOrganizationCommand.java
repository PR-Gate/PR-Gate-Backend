package com.prgate.projects.application.command;

import java.util.UUID;

public record LinkGitHubOrganizationCommand(UUID projectId, String organizationLogin) { }
