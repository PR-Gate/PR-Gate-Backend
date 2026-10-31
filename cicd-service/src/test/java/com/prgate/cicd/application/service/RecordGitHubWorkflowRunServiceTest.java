package com.prgate.cicd.application.service;

import com.prgate.cicd.application.model.*;
import com.prgate.cicd.application.port.in.RecordGitHubWorkflowRunUseCase.Result;
import com.prgate.cicd.application.port.out.*;
import com.prgate.cicd.domain.model.aggregates.*;
import com.prgate.cicd.domain.model.enums.*;
import com.prgate.cicd.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordGitHubWorkflowRunServiceTest {
    private final PipelineRunRepository runs = mock(PipelineRunRepository.class);
    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final EvaluationRepositoryLookupPort registrations = mock(EvaluationRepositoryLookupPort.class);
    private final Instant now = Instant.parse("2026-10-30T12:00:00Z");
    private final GitHubRepository github = new GitHubRepository(123, "team", "backend");
    private final RecordGitHubWorkflowRunService service = new RecordGitHubWorkflowRunService(
            runs, deliveries, Clock.fixed(now, ZoneOffset.UTC), "PRGate Evaluation", registrations);

    private GitHubWorkflowRunEvent event() {
        return new GitHubWorkflowRunEvent(new GitHubDeliveryId(UUID.randomUUID()), WorkflowRunAction.COMPLETED,
                github, new GitHubWorkflowRunId(456), new GitHubRunAttempt(1), "PRGate Evaluation", "feature/api",
                new GitCommitSha("a".repeat(40)), PipelineTrigger.PUSH,
                URI.create("https://github.com/team/backend/actions/runs/456"),
                now.minusSeconds(60), now.minusSeconds(50), PipelineConclusion.SUCCESS, now);
    }

    @Test
    void unknownRepositoryDoesNotClaimDeliveryOrCreateRun() {
        when(registrations.findByGithubRepositoryId(123)).thenReturn(Optional.empty());
        assertEquals(Result.IGNORED_UNREGISTERED_REPOSITORY, service.record(event()));
        verifyNoInteractions(runs, deliveries);
    }

    @Test
    void attachesAcademicReferenceAndKeepsFeatureBranchPipeline() {
        var registration = EvaluationRepository.register(EvaluationRepositoryId.generate(),
                new AcademicProjectId(UUID.randomUUID()), github, "main", now.minusSeconds(120));
        when(registrations.findByGithubRepositoryId(123)).thenReturn(Optional.of(registration));
        when(deliveries.claimIfUnprocessed(any(), any())).thenReturn(true);
        when(runs.findByGitHubIdentity(any(), any(), any())).thenReturn(Optional.empty());
        assertEquals(Result.RECORDED, service.record(event()));
        var saved = ArgumentCaptor.forClass(PipelineRun.class);
        verify(runs).save(saved.capture());
        var run = saved.getValue();
        assertEquals(registration.getProjectId(), run.getProjectReference().orElseThrow().projectId());
        assertEquals("feature/api", run.getBranch());
        assertEquals(Duration.ofSeconds(50), run.getDuration().orElseThrow());
    }

    @Test
    void duplicateDeliveryDoesNotUpdateRun() {
        var registration = EvaluationRepository.register(EvaluationRepositoryId.generate(),
                new AcademicProjectId(UUID.randomUUID()), github, "main", now);
        when(registrations.findByGithubRepositoryId(123)).thenReturn(Optional.of(registration));
        when(deliveries.claimIfUnprocessed(any(), any())).thenReturn(false);
        assertEquals(Result.DUPLICATE_DELIVERY, service.record(event()));
        verifyNoInteractions(runs);
    }
}
