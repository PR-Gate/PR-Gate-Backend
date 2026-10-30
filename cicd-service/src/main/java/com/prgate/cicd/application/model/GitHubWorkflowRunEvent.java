package com.prgate.cicd.application.model;

import com.prgate.cicd.domain.model.enums.PipelineConclusion;
import com.prgate.cicd.domain.model.enums.PipelineTrigger;
import com.prgate.cicd.domain.model.valueobject.GitCommitSha;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.GitHubRunAttempt;
import com.prgate.cicd.domain.model.valueobject.GitHubWorkflowRunId;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/** Application-level representation of the supported data from a workflow_run event. */
public record GitHubWorkflowRunEvent(
        GitHubDeliveryId deliveryId,
        WorkflowRunAction action,
        GitHubRepository repository,
        GitHubWorkflowRunId runId,
        GitHubRunAttempt attempt,
        String workflowName,
        String branch,
        GitCommitSha commitSha,
        PipelineTrigger trigger,
        URI runUrl,
        Instant createdAt,
        Instant startedAt,
        PipelineConclusion conclusion,
        Instant completedAt) {

    public GitHubWorkflowRunEvent {
        Objects.requireNonNull(deliveryId, "GitHub delivery id is required");
        Objects.requireNonNull(action, "Workflow run action is required");
        Objects.requireNonNull(repository, "GitHub repository is required");
        Objects.requireNonNull(runId, "GitHub workflow run id is required");
        Objects.requireNonNull(attempt, "GitHub workflow run attempt is required");
        workflowName = requireText(workflowName, "Workflow name", 200);
        branch = requireText(branch, "Branch", 255);
        Objects.requireNonNull(commitSha, "Commit SHA is required");
        Objects.requireNonNull(trigger, "Pipeline trigger is required");
        Objects.requireNonNull(runUrl, "Workflow run URL is required");
        Objects.requireNonNull(createdAt, "Creation time is required");

        if (action == WorkflowRunAction.IN_PROGRESS && startedAt == null) {
            throw new IllegalArgumentException("Start time is required for an in-progress event");
        }
        if (action == WorkflowRunAction.COMPLETED && (conclusion == null || completedAt == null)) {
            throw new IllegalArgumentException("Conclusion and completion time are required for a completed event");
        }
    }

    private static String requireText(String value, String label, int maxLength) {
        Objects.requireNonNull(value, label + " is required");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " must contain between 1 and " + maxLength + " characters");
        }
        return normalized;
    }
}
