package com.prgate.metrics.domain.model.valueobjects;
public record ExpectedAnalysis(String projectKey, String taskId, AnalysisScope scope) {
    public ExpectedAnalysis { projectKey = Checks.text(projectKey); taskId = Checks.text(taskId); Checks.required(scope); }
}
