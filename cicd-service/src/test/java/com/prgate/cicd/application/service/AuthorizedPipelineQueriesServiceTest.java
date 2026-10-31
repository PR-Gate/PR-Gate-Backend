package com.prgate.cicd.application.service;

import com.prgate.cicd.application.port.in.QueryPipelineRunsUseCase;
import com.prgate.cicd.application.port.out.*;
import com.prgate.cicd.domain.model.aggregates.*;
import com.prgate.cicd.domain.model.enums.PipelineTrigger;
import com.prgate.cicd.domain.model.valueobject.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizedPipelineQueriesServiceTest {
    private final QueryPipelineRunsUseCase queries = mock(QueryPipelineRunsUseCase.class);
    private final EvaluationRepositoryLookupPort repositories = mock(EvaluationRepositoryLookupPort.class);
    private final ProjectEvaluationAccessPort access = mock(ProjectEvaluationAccessPort.class);
    private final AuthorizedPipelineQueriesService service = new AuthorizedPipelineQueriesService(queries, repositories, access);
    private final UUID user = UUID.randomUUID();
    private final AcademicProjectId project = new AcademicProjectId(UUID.randomUUID());
    private final EvaluationRepository repository = EvaluationRepository.register(EvaluationRepositoryId.generate(),
            project, new GitHubRepository(123, "team", "backend"), "main", Instant.now());

    private PipelineRun run(AcademicProjectId projectId) {
        var run = PipelineRun.queued(PipelineRunId.generate(), new GitHubWorkflowRunId(456), new GitHubRunAttempt(1),
                repository.getGithubRepository(), "PRGate Evaluation", "main", new GitCommitSha("a".repeat(40)),
                PipelineTrigger.PUSH, URI.create("https://github.com/team/backend/actions/runs/456"), Instant.now());
        run.associateWith(new PipelineProjectReference(repository.getId(), projectId));
        return run;
    }

    @Test
    void knowingPipelineUuidDoesNotGrantAccess() {
        var run = run(project);
        when(queries.findById(run.getId().value())).thenReturn(Optional.of(run));
        assertTrue(service.findById(user, run.getId().value()).isEmpty());
        verify(access).mayReadEvaluations(user, project);
    }

    @Test
    void deniedListingDoesNotFetchPipelineData() {
        when(repositories.findByGithubRepositoryId(123)).thenReturn(Optional.of(repository));
        assertTrue(service.list(user, 123, null, 0, 20).isEmpty());
        verifyNoInteractions(queries);
    }

    @Test
    void allowedProjectDoesNotExposeAnotherProjectsHistoricalSnapshot() {
        when(repositories.findByGithubRepositoryId(123)).thenReturn(Optional.of(repository));
        when(access.mayReadEvaluations(user, project)).thenReturn(true);
        var permitted = run(project);
        var other = run(new AcademicProjectId(UUID.randomUUID()));
        when(queries.list(123, null, 0, 20)).thenReturn(List.of(permitted, other));
        assertEquals(List.of(permitted), service.list(user, 123, null, 0, 20));
    }
}
