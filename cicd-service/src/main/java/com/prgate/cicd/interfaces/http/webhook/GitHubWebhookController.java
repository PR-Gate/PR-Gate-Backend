package com.prgate.cicd.interfaces.http.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.prgate.cicd.application.model.GitHubDeliveryId;
import com.prgate.cicd.application.model.GitHubWorkflowRunEvent;
import com.prgate.cicd.application.model.WorkflowRunAction;
import com.prgate.cicd.application.port.in.RecordGitHubWorkflowRunUseCase;
import com.prgate.cicd.domain.model.enums.PipelineConclusion;
import com.prgate.cicd.domain.model.enums.PipelineTrigger;
import com.prgate.cicd.domain.model.valueobject.GitCommitSha;
import com.prgate.cicd.domain.model.valueobject.GitHubRepository;
import com.prgate.cicd.domain.model.valueobject.GitHubRunAttempt;
import com.prgate.cicd.domain.model.valueobject.GitHubWorkflowRunId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/webhooks/github")
public class GitHubWebhookController {

    private static final String WORKFLOW_RUN_EVENT = "workflow_run";

    private final RecordGitHubWorkflowRunUseCase recordWorkflowRun;

    public GitHubWebhookController(RecordGitHubWorkflowRunUseCase recordWorkflowRun) {
        this.recordWorkflowRun = recordWorkflowRun;
    }

    @PostMapping
    public ResponseEntity<WebhookAcknowledgement> receive(
            @RequestHeader("X-GitHub-Event") String githubEvent,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestBody JsonNode payload) {
        if (!WORKFLOW_RUN_EVENT.equals(githubEvent)) {
            return ResponseEntity.accepted().body(new WebhookAcknowledgement("IGNORED_EVENT"));
        }

        String actionValue = requiredText(payload, "action");
        WorkflowRunAction action = parseAction(actionValue);
        if (action == null) {
            return ResponseEntity.accepted().body(new WebhookAcknowledgement("IGNORED_ACTION"));
        }

        JsonNode workflowRun = requiredObject(payload, "workflow_run");
        PipelineTrigger trigger = parseTrigger(requiredText(workflowRun, "event"));
        if (trigger == null) {
            return ResponseEntity.accepted().body(new WebhookAcknowledgement("IGNORED_TRIGGER"));
        }

        GitHubWorkflowRunEvent event = toApplicationEvent(
                GitHubDeliveryId.from(deliveryId), action, workflowRun, requiredObject(payload, "repository"), trigger);
        RecordGitHubWorkflowRunUseCase.Result result = recordWorkflowRun.record(event);
        return switch (result) {
            case DUPLICATE_DELIVERY -> ResponseEntity.ok(new WebhookAcknowledgement(result.name()));
            case RECORDED, IGNORED_WORKFLOW -> ResponseEntity.accepted().body(new WebhookAcknowledgement(result.name()));
        };
    }

    private GitHubWorkflowRunEvent toApplicationEvent(GitHubDeliveryId deliveryId, WorkflowRunAction action,
                                                       JsonNode workflowRun, JsonNode repository,
                                                       PipelineTrigger trigger) {
        JsonNode owner = requiredObject(repository, "owner");
        Instant completedAt = optionalInstant(workflowRun, "updated_at", action == WorkflowRunAction.COMPLETED);
        PipelineConclusion conclusion = action == WorkflowRunAction.COMPLETED
                ? parseConclusion(requiredText(workflowRun, "conclusion"))
                : null;

        return new GitHubWorkflowRunEvent(
                deliveryId,
                action,
                new GitHubRepository(requiredLong(repository, "id"), requiredText(owner, "login"),
                        requiredText(repository, "name")),
                new GitHubWorkflowRunId(requiredLong(workflowRun, "id")),
                new GitHubRunAttempt(requiredInteger(workflowRun, "run_attempt")),
                requiredText(workflowRun, "name"),
                requiredText(workflowRun, "head_branch"),
                new GitCommitSha(requiredText(workflowRun, "head_sha")),
                trigger,
                URI.create(requiredText(workflowRun, "html_url")),
                requiredInstant(workflowRun, "created_at"),
                optionalInstant(workflowRun, "run_started_at", action == WorkflowRunAction.IN_PROGRESS),
                conclusion,
                completedAt);
    }

    private static WorkflowRunAction parseAction(String value) {
        return switch (value) {
            case "requested" -> WorkflowRunAction.REQUESTED;
            case "in_progress" -> WorkflowRunAction.IN_PROGRESS;
            case "completed" -> WorkflowRunAction.COMPLETED;
            default -> null;
        };
    }

    private static PipelineTrigger parseTrigger(String value) {
        return switch (value) {
            case "push" -> PipelineTrigger.PUSH;
            case "pull_request" -> PipelineTrigger.PULL_REQUEST;
            default -> null;
        };
    }

    private static PipelineConclusion parseConclusion(String value) {
        try {
            return PipelineConclusion.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badPayload("Unsupported workflow conclusion");
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isObject()) {
            throw badPayload("Missing or invalid object: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw badPayload("Missing or invalid text field: " + field);
        }
        return value.asText();
    }

    private static long requiredLong(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || (!value.isIntegralNumber() || !value.canConvertToLong()) || value.longValue() <= 0) {
            throw badPayload("Missing or invalid numeric field: " + field);
        }
        return value.longValue();
    }

    private static int requiredInteger(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || (!value.isIntegralNumber() || !value.canConvertToInt()) || value.intValue() < 1) {
            throw badPayload("Missing or invalid numeric field: " + field);
        }
        return value.intValue();
    }

    private static Instant requiredInstant(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual()) {
            throw badPayload("Missing or invalid timestamp field: " + field);
        }
        try {
            return Instant.parse(value.asText());
        } catch (RuntimeException exception) {
            throw badPayload("Invalid timestamp field: " + field);
        }
    }

    private static Instant optionalInstant(JsonNode parent, String field, boolean required) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            if (required) {
                throw badPayload("Missing timestamp field: " + field);
            }
            return null;
        }
        if (!value.isTextual()) {
            throw badPayload("Invalid timestamp field: " + field);
        }
        try {
            return Instant.parse(value.asText());
        } catch (RuntimeException exception) {
            throw badPayload("Invalid timestamp field: " + field);
        }
    }

    private static ResponseStatusException badPayload(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record WebhookAcknowledgement(String result) {
    }
}
