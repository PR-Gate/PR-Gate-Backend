package com.prgate.cicd.domain.model.valueobject;

import java.util.Objects;

/** Snapshot of the academic association when the workflow was registered. */
public record PipelineProjectReference(EvaluationRepositoryId evaluationRepositoryId, AcademicProjectId projectId) {
    public PipelineProjectReference {
        Objects.requireNonNull(evaluationRepositoryId);
        Objects.requireNonNull(projectId);
    }
}
