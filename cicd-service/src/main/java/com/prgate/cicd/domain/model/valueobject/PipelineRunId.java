package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;
import java.util.UUID;

public record PipelineRunId(UUID value) {

    public PipelineRunId {
        Objects.requireNonNull(value, "Pipeline run id is required");
    }

    public static PipelineRunId generate() {
        return new PipelineRunId(UUID.randomUUID());
    }
}
