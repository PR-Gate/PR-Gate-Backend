package com.prgate.cicd.application.service;

import com.prgate.cicd.application.model.GitHubWorkflowRunEvent;
import com.prgate.cicd.application.model.WorkflowRunAction;
import com.prgate.cicd.application.port.in.RecordGitHubWorkflowRunUseCase;
import com.prgate.cicd.application.port.out.PipelineRunRepository;
import com.prgate.cicd.application.port.out.WebhookDeliveryRepository;
import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import com.prgate.cicd.domain.model.enums.PipelineRunStatus;
import com.prgate.cicd.domain.model.valueobject.PipelineRunId;

import java.time.Clock;
import java.util.Objects;

public final class RecordGitHubWorkflowRunService implements RecordGitHubWorkflowRunUseCase {

    private final PipelineRunRepository pipelineRunRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final Clock clock;
    private final String dedicatedWorkflowName;

    public RecordGitHubWorkflowRunService(PipelineRunRepository pipelineRunRepository,
                                          WebhookDeliveryRepository webhookDeliveryRepository,
                                          Clock clock,
                                          String dedicatedWorkflowName) {
        this.pipelineRunRepository = Objects.requireNonNull(pipelineRunRepository,
                "Pipeline run repository is required");
        this.webhookDeliveryRepository = Objects.requireNonNull(webhookDeliveryRepository,
                "Webhook delivery repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.dedicatedWorkflowName = requireWorkflowName(dedicatedWorkflowName);
    }

    @Override
    public Result record(GitHubWorkflowRunEvent event) {
        Objects.requireNonNull(event, "Workflow run event is required");
        if (!dedicatedWorkflowName.equals(event.workflowName())) {
            return Result.IGNORED_WORKFLOW;
        }

        if (!webhookDeliveryRepository.claimIfUnprocessed(event.deliveryId(), clock.instant())) {
            return Result.DUPLICATE_DELIVERY;
        }

        PipelineRun pipelineRun = pipelineRunRepository
                .findByGitHubIdentity(event.repository(), event.runId(), event.attempt())
                .orElseGet(() -> newPipelineRun(event));

        applyAction(pipelineRun, event);
        pipelineRunRepository.save(pipelineRun);
        return Result.RECORDED;
    }

    private PipelineRun newPipelineRun(GitHubWorkflowRunEvent event) {
        return PipelineRun.queued(
                PipelineRunId.generate(),
                event.runId(),
                event.attempt(),
                event.repository(),
                event.workflowName(),
                event.branch(),
                event.commitSha(),
                event.trigger(),
                event.runUrl(),
                event.createdAt());
    }

    private void applyAction(PipelineRun pipelineRun, GitHubWorkflowRunEvent event) {
        if (pipelineRun.getStatus() == PipelineRunStatus.COMPLETED
                && event.action() != WorkflowRunAction.COMPLETED) {
            return;
        }

        switch (event.action()) {
            case REQUESTED -> {
                // A newly created aggregate is already queued.
            }
            case IN_PROGRESS -> pipelineRun.start(event.startedAt());
            case COMPLETED -> { if (pipelineRun.getStatus() == PipelineRunStatus.QUEUED && event.startedAt() != null) { pipelineRun.start(event.startedAt()); } pipelineRun.complete(event.conclusion(), event.completedAt()); }
        }
    }

    private static String requireWorkflowName(String value) {
        Objects.requireNonNull(value, "Dedicated workflow name is required");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException("Dedicated workflow name must contain between 1 and 200 characters");
        }
        return normalized;
    }
}
