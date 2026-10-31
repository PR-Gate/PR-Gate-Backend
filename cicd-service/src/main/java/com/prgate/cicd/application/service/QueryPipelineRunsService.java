package com.prgate.cicd.application.service;

import com.prgate.cicd.application.port.in.QueryPipelineRunsUseCase;
import com.prgate.cicd.application.port.out.PipelineRunQueryPort;
import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class QueryPipelineRunsService implements QueryPipelineRunsUseCase {
    private final PipelineRunQueryPort queryPort;

    public QueryPipelineRunsService(PipelineRunQueryPort queryPort) {
        this.queryPort = Objects.requireNonNull(queryPort);
    }

    @Override
    public Optional<PipelineRun> findById(UUID id) {
        return queryPort.findById(Objects.requireNonNull(id));
    }

    @Override
    public List<PipelineRun> list(long repositoryId, String branch, int page, int size) {
        if (repositoryId <= 0 || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Repository must be positive, page nonnegative and size between 1 and 100");
        }
        if (branch != null && (branch.isBlank() || branch.length() > 255)) {
            throw new IllegalArgumentException("Branch must contain between 1 and 255 characters");
        }
        return queryPort.findByRepository(repositoryId, branch, page, size);
    }
}
