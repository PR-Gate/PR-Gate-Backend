package com.prgate.metrics.domain.model.valueobjects;
import java.util.UUID;
public record TechnicalPlanReference(UUID planId, RubricId rubricId, RubricVersion rubricVersion) {
    public TechnicalPlanReference { Checks.required(planId); Checks.required(rubricId); Checks.required(rubricVersion); }
}
