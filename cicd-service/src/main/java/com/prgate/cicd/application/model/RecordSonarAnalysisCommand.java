package com.prgate.cicd.application.model;

import com.prgate.cicd.domain.model.valueobject.*;
import java.util.Objects;

public record RecordSonarAnalysisCommand(long githubRepositoryId, GitHubWorkflowRunId runId,
        GitHubRunAttempt attempt, SonarProjectKey projectKey, String branch,
        GitCommitSha revision, SonarAnalysisResult analysis) {
    public RecordSonarAnalysisCommand {
        if (githubRepositoryId <= 0) { throw new IllegalArgumentException("Repository id must be positive"); }
        Objects.requireNonNull(runId); Objects.requireNonNull(attempt); Objects.requireNonNull(projectKey);
        Objects.requireNonNull(branch); Objects.requireNonNull(revision); Objects.requireNonNull(analysis);
    }
}
