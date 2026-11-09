package com.prgate.cicd.application.port.in;

import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryPipelineRunsUseCase {
    Optional<PipelineRun> findById(UUID id);
    List<PipelineRun> list(long repositoryId, String branch, int page, int size);
}
