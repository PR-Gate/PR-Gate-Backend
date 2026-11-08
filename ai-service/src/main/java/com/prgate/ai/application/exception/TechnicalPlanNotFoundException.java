package com.prgate.ai.application.exception;

import com.prgate.ai.domain.model.valueobjects.PlanId;

public final class TechnicalPlanNotFoundException extends RuntimeException {
    public TechnicalPlanNotFoundException(PlanId id) {
        super("Technical plan not found: " + id.value());
    }
}
