package com.prgate.cicd.infrastructure.sonar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Builds only the versioned integration message, without assigning academic grades. */
@Component
public class CompletedAnalysisEventFactory {
    private final ObjectMapper json;

    public CompletedAnalysisEventFactory(ObjectMapper json) { this.json = json; }

    public Event build(Source source, JsonNode webhook, SonarProjectHttpAdapter.VerifiedAnalysis analysis) {
        var properties = webhook.path("properties");
        long runId = Long.parseLong(required(properties, "sonar.analysis.prgateWorkflowRunId"));
        int attempt = Integer.parseInt(required(properties, "sonar.analysis.prgateWorkflowRunAttempt"));
        if (runId <= 0 || attempt <= 0 || source.repositoryId() <= 0) {
            throw new IllegalArgumentException("Repository, run and attempt must be positive");
        }
        String projectKey = required(webhook.path("project"), "key");
        String taskId = required(webhook, "taskId");
        String revision = required(webhook, "revision");
        if (!source.revision().equals(revision)
                || !Long.toString(source.repositoryId()).equals(required(properties, "sonar.analysis.prgateRepositoryId"))
                || !source.gitRef().equals(required(properties, "sonar.analysis.prgatePushRef"))
                || !source.beforeSha().equals(required(properties, "sonar.analysis.prgatePushBefore"))) {
            throw new IllegalArgumentException("Analysis does not match push receipt");
        }
        Instant analyzedAt = timestamp(required(webhook, "analysedAt"));
        // Stable across retries, restarts and differently serialized receipts for the same task.
        UUID eventId = UUID.nameUUIDFromBytes(("cicd.analysis.completed.v1\n" + source.repositoryId()
                + "\n" + projectKey + "\n" + taskId + "\n" + runId + "\n" + attempt)
                .getBytes(StandardCharsets.UTF_8));
        var event = json.createObjectNode();
        event.put("schemaVersion", 1).put("eventType", "cicd.analysis.completed.v1")
                .put("eventId", eventId.toString()).put("occurredAt", analyzedAt.toString())
                .put("repositoryId", source.repositoryId()).put("repository", source.repository())
                .put("gitRef", source.gitRef()).put("commitSha", revision)
                .put("workflowRunId", runId).put("workflowRunAttempt", attempt)
                .put("sonarProjectKey", projectKey).put("sonarTaskId", taskId)
                .put("sonarAnalysisId", analysis.analysisId()).put("analyzedAt", analyzedAt.toString())
                .put("qualityGateStatus", analysis.qualityGateStatus());
        // No current-project measures: they may already belong to a newer revision.
        // Full collection of analysis-specific observations is a separate adapter.
        event.putObject("measures");
        return new Event(eventId, event.toString());
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Missing analysis metadata: " + field);
        return value;
    }

    private static Instant timestamp(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (java.time.format.DateTimeParseException exception) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        }
    }

    public record Source(long repositoryId, String repository, String gitRef, String beforeSha, String revision) { }
    public record Event(UUID id, String payload) { }
}
