package com.prgate.metrics.domain.model.valueobjects;
public record RuleCheck(String ruleKey) implements CheckSpecification {
    public RuleCheck { ruleKey = Checks.text(ruleKey); }
}
