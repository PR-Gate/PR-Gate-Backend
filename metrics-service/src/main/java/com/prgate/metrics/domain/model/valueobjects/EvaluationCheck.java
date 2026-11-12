package com.prgate.metrics.domain.model.valueobjects;
/** All configured checks are mandatory in the initial scoring policy. */
public record EvaluationCheck(CheckId id, CriterionId criterionId, CheckSpecification specification) {
    public EvaluationCheck { Checks.required(id); Checks.required(criterionId); Checks.required(specification); }
}
