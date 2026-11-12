package com.prgate.metrics.domain.model.valueobjects;
import java.util.OptionalInt;
public record IssueEvidence(String issueKey, String ruleKey, String component, OptionalInt line, String message) {
    public IssueEvidence {
        issueKey = Checks.text(issueKey); ruleKey = Checks.text(ruleKey);
        component = Checks.text(component); Checks.required(line); message = Checks.text(message);
        Checks.require(line.isEmpty() || line.getAsInt() > 0, "issue line must be positive");
    }
}
