package com.prgate.cicd.application.service;

import com.prgate.cicd.application.model.RecordSonarAnalysisCommand;
import com.prgate.cicd.application.port.out.*;
import com.prgate.cicd.domain.model.aggregates.*;
import com.prgate.cicd.domain.model.enums.*;
import com.prgate.cicd.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordSonarAnalysisServiceTest {
    private final EvaluationRepositoryLookupPort registrations = mock(EvaluationRepositoryLookupPort.class);
    private final PipelineRunRepository runs = mock(PipelineRunRepository.class);
    private final RecordSonarAnalysisService service = new RecordSonarAnalysisService(registrations, runs);
    private final Instant now = Instant.parse("2026-10-30T12:00:00Z");
    private final GitHubRepository github = new GitHubRepository(123, "team", "backend");
    private final EvaluationRepository registration = EvaluationRepository.register(EvaluationRepositoryId.generate(),
            new AcademicProjectId(UUID.randomUUID()), github, "main", now);
    private final GitHubWorkflowRunId runId = new GitHubWorkflowRunId(456);
    private final GitHubRunAttempt attempt = new GitHubRunAttempt(2);
    private final GitCommitSha revision = new GitCommitSha("a".repeat(40));
    private final SonarAnalysisResult analysis = new SonarAnalysisResult("analysis-1", QualityGateStatus.OK,
            new SonarMetrics(null, null, null, null), URI.create("http://localhost:9000/dashboard?id=test"), now);

    private PipelineRun run() {
        var run = PipelineRun.queued(PipelineRunId.generate(), runId, attempt, github,
                "PRGate Evaluation", "main", revision, PipelineTrigger.PUSH,
                URI.create("https://github.com/team/backend/actions/runs/456"), now);
        run.associateWith(new PipelineProjectReference(registration.getId(), registration.getProjectId()));
        return run;
    }
    private RecordSonarAnalysisCommand command(GitCommitSha sha) {
        return new RecordSonarAnalysisCommand(123, runId, attempt, registration.getSonarProjectKey(), "main", sha, analysis);
    }
    @Test
    void exactCorrelationIsIdempotentAndUnknownMetricsRemainAbsent() {
        var run = run();
        when(registrations.findByGithubRepositoryId(123)).thenReturn(Optional.of(registration));
        when(runs.findByGitHubIdentity(github, runId, attempt)).thenReturn(Optional.of(run));
        service.record(command(revision)); service.record(command(revision));
        assertEquals("analysis-1", run.getSonarAnalysis().orElseThrow().analysisId());
        assertNull(run.getSonarAnalysis().orElseThrow().metrics().coveragePercent());
    }
    @Test
    void mismatchingCommitCannotAttachAnalysis() {
        var run = run();
        when(registrations.findByGithubRepositoryId(123)).thenReturn(Optional.of(registration));
        when(runs.findByGitHubIdentity(github, runId, attempt)).thenReturn(Optional.of(run));
        assertThrows(IllegalArgumentException.class, () -> service.record(command(new GitCommitSha("b".repeat(40)))));
        verify(runs, never()).save(any());
    }
    @Test
    void missingPipelineRemainsRetryable() {
        when(registrations.findByGithubRepositoryId(123)).thenReturn(Optional.of(registration));
        when(runs.findByGitHubIdentity(github, runId, attempt)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.record(command(revision)));
        verify(runs, never()).save(any());
    }
}
