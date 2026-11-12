package com.prgate.metrics.domain.model.valueobjects;
public record SonarAnalysisReference(ExpectedAnalysis source, String analysisId, String commitSha) {
    public SonarAnalysisReference {
        Checks.required(source); analysisId = Checks.text(analysisId);
        commitSha = Checks.text(commitSha);
        Checks.require(commitSha.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"), "full commit SHA required");
        commitSha = commitSha.toLowerCase(java.util.Locale.ROOT);
    }
}
