package com.prgate.cicd.domain.model.valueobject;

public record GitHubWorkflowRunId(long value) {

    public GitHubWorkflowRunId {
        if (value <= 0) {
            throw new IllegalArgumentException("GitHub workflow run id must be positive");
        }
    }
}
