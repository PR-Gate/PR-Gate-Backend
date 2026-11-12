package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record SubmissionReference(UUID submissionId, UUID studentId, String commitSha) {
    public SubmissionReference {
        Checks.required(submissionId); Checks.required(studentId); commitSha = Checks.text(commitSha);
        Checks.require(commitSha.matches("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}"), "full commit SHA required");
        commitSha = commitSha.toLowerCase(java.util.Locale.ROOT);
    }
}
