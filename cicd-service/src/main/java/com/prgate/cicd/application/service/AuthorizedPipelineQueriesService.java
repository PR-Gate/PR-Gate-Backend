package com.prgate.cicd.application.service;

import com.prgate.cicd.application.port.in.AuthorizedPipelineQueriesUseCase;
import com.prgate.cicd.application.port.in.QueryPipelineRunsUseCase;
import com.prgate.cicd.application.port.out.EvaluationRepositoryLookupPort;
import com.prgate.cicd.application.port.out.ProjectEvaluationAccessPort;
import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AuthorizedPipelineQueriesService implements AuthorizedPipelineQueriesUseCase {
    private final QueryPipelineRunsUseCase queries;
    private final EvaluationRepositoryLookupPort repositories;
    private final ProjectEvaluationAccessPort access;

    public AuthorizedPipelineQueriesService(QueryPipelineRunsUseCase queries,
            EvaluationRepositoryLookupPort repositories, ProjectEvaluationAccessPort access) {
        this.queries = Objects.requireNonNull(queries);
        this.repositories = Objects.requireNonNull(repositories);
        this.access = Objects.requireNonNull(access);
    }

    @Override
    public Optional<PipelineRun> findById(UUID userId, UUID pipelineId) {
        Objects.requireNonNull(userId, "Verified user identity is required");
        return queries.findById(pipelineId).filter(run -> run.getProjectReference()
                .map(reference -> access.mayReadEvaluations(userId, reference.projectId())).orElse(false));
    }

    @Override
    public List<PipelineRun> list(UUID userId, long repositoryId, String branch, int page, int size) {
        Objects.requireNonNull(userId, "Verified user identity is required");
        if (repositoryId <= 0 || page < 0 || size < 1 || size > 100
                || (branch != null && (branch.isBlank() || branch.length() > 255))) {
            throw new IllegalArgumentException("Invalid repository, branch or pagination");
        }
        var repository = repositories.findByGithubRepositoryId(repositoryId);
        if (repository.isEmpty() || !access.mayReadEvaluations(userId, repository.orElseThrow().getProjectId())) {
            return List.of();
        }
        // Also check historical snapshots: future reassignment must not leak a previous project's runs.
        return queries.list(repositoryId, branch, page, size).stream()
                .filter(run -> run.getProjectReference()
                        .map(reference -> reference.projectId().equals(repository.orElseThrow().getProjectId()))
                        .orElse(false)).toList();
    }
}
