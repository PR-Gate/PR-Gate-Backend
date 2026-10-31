package com.prgate.cicd.application.model;

import java.util.Objects;
import java.util.UUID;

/** Trusted integration command enriched from Projects' organization link and GitHub repository discovery. */
public record ConfigureRepositoryEvaluationCommand(UUID projectId, long githubRepositoryId,
        String githubOwner, String githubName, String analysisBranch) {
    public ConfigureRepositoryEvaluationCommand {
        Objects.requireNonNull(projectId, "Projects project id is required");
        Objects.requireNonNull(githubOwner, "GitHub owner is required");
        Objects.requireNonNull(githubName, "GitHub repository name is required");
        Objects.requireNonNull(analysisBranch, "Analysis branch is required");
        if (githubRepositoryId <= 0) {
            throw new IllegalArgumentException("GitHub repository id must be positive");
        }
    }
}
