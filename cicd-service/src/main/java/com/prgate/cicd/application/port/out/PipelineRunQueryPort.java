package com.prgate.cicd.application.port.out;

import com.prgate.cicd.domain.model.aggregates.PipelineRun;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineRunQueryPort {
    Optional<PipelineRun> findById(UUID id);
    List<PipelineRun> findByRepository(long repositoryId, String branch, int page, int size);
}
