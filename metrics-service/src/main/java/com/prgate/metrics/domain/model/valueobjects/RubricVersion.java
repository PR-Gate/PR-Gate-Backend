package com.prgate.metrics.domain.model.valueobjects;
public record RubricVersion(int value) {
    public RubricVersion { Checks.require(value > 0, "rubric version must be positive"); }
    public RubricVersion next() {
        Checks.require(value < Integer.MAX_VALUE, "rubric version exhausted");
        return new RubricVersion(value + 1);
    }
}
