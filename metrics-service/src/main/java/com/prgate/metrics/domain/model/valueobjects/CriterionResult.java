package com.prgate.metrics.domain.model.valueobjects;
import com.prgate.metrics.domain.model.enums.CheckStatus;
import java.util.*;
public record CriterionResult(CriterionId criterionId, Weight weight, CheckStatus status, List<CheckResult> checks) {
    public CriterionResult {
        Checks.required(criterionId); Checks.required(weight); Checks.required(status);
        checks = List.copyOf(Checks.required(checks));
        Checks.require(!checks.isEmpty(), "criterion result needs checks");
    }
}
