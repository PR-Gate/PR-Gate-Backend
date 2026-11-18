package com.prgate.metrics.domain.model.valueobjects;
import java.util.*;
/** Technical compliance percentages, never a grade estimate. Complete compliance requires all criteria evaluated. */
public record EvaluationSummary(List<CriterionResult> criteria, CompliancePercentage coverage,
        Optional<CompliancePercentage> provisionalPercentage, Optional<CompliancePercentage> finalPercentage) {
    public EvaluationSummary {
        criteria = List.copyOf(Checks.required(criteria)); Checks.required(coverage);
        Checks.required(provisionalPercentage); Checks.required(finalPercentage);
    }
}
