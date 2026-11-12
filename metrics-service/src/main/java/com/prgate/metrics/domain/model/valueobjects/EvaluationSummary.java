package com.prgate.metrics.domain.model.valueobjects;
import java.util.*;
/** Provisional score is relative to evaluated weight. Final score requires 100% coverage. */
public record EvaluationSummary(List<CriterionResult> criteria, CompliancePercentage coverage,
        Optional<CompliancePercentage> provisionalPercentage, Optional<CompliancePercentage> finalPercentage) {
    public EvaluationSummary {
        criteria = List.copyOf(Checks.required(criteria)); Checks.required(coverage);
        Checks.required(provisionalPercentage); Checks.required(finalPercentage);
    }
}
