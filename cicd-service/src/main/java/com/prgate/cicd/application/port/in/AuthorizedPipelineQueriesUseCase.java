package com.prgate.cicd.application.port.in;

import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The user id must be resolved from a verified identity, never a query/body parameter. */
public interface AuthorizedPipelineQueriesUseCase {
    Optional<PipelineRun> findById(UUID authenticatedUserId, UUID pipelineId);
    List<PipelineRun> list(UUID authenticatedUserId, long repositoryId, String branch, int page, int size);
}
