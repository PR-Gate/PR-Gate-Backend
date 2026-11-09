package com.prgate.cicd.domain.model.valueobject;

public record GitHubRunAttempt(int value) {

    public GitHubRunAttempt {
        if (value < 1) {
            throw new IllegalArgumentException("GitHub workflow run attempt must be at least 1");
        }
    }
}
