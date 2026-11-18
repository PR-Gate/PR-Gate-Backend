package com.prgate.metrics.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Integration contract. Missing measures are unknown, never implicitly zero. */
public record AnalysisCompletedEvent(
        int schemaVersion, String eventType, UUID eventId, Instant occurredAt,
        long repositoryId, String repository, String gitRef, String commitSha,
        long workflowRunId, int workflowRunAttempt, String sonarProjectKey,
        String sonarTaskId, String sonarAnalysisId, Instant analyzedAt,
        String qualityGateStatus, Map<String, BigDecimal> measures) {

    public static final String TYPE = "cicd.analysis.completed.v1";

    public AnalysisCompletedEvent {
        if (schemaVersion != 1 || !TYPE.equals(eventType)) {
            throw new IllegalArgumentException("Unsupported analysis event version or type");
        }
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(analyzedAt, "analyzedAt");
        if (repositoryId <= 0 || workflowRunId <= 0 || workflowRunAttempt <= 0) {
            throw new IllegalArgumentException("Repository, run and attempt must be positive");
        }
        requireText(repository, "repository");
        requireText(gitRef, "gitRef");
        if (!gitRef.startsWith("refs/heads/")) {
            throw new IllegalArgumentException("A full branch reference is required");
        }
        if (commitSha == null || !commitSha.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A lowercase full commit SHA is required");
        }
        requireText(sonarProjectKey, "sonarProjectKey");
        requireText(sonarTaskId, "sonarTaskId");
        requireText(sonarAnalysisId, "sonarAnalysisId");
        if (!java.util.Set.of("OK", "WARN", "ERROR", "NONE").contains(
                Objects.requireNonNull(qualityGateStatus, "qualityGateStatus"))) {
            throw new IllegalArgumentException("Unsupported Quality Gate status");
        }
        measures = Map.copyOf(Objects.requireNonNull(measures, "measures"));
        measures.keySet().forEach(key -> requireText(key, "metric key"));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
