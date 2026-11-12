package com.prgate.metrics.domain.model.valueobjects;
import java.util.*;
/** Completeness includes pagination and scope coverage, verified by the outbound adapter. */
public record RuleObservation(CheckId checkId, String ruleKey, boolean ruleExecuted,
        boolean evidenceComplete, List<IssueEvidence> issues) implements CheckObservation {
    public RuleObservation {
        Checks.required(checkId); ruleKey = Checks.text(ruleKey);
        issues = List.copyOf(Checks.required(issues));
        var key = ruleKey;
        Checks.require(issues.stream().allMatch(i -> i.ruleKey().equals(key)), "issue belongs to another rule");
        Checks.require(issues.stream().map(IssueEvidence::issueKey).distinct().count() == issues.size(),
                "duplicate issue evidence");
    }
}
