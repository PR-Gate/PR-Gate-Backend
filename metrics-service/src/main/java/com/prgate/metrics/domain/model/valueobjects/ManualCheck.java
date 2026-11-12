package com.prgate.metrics.domain.model.valueobjects;
public record ManualCheck(String instructions) implements CheckSpecification {
    public ManualCheck { instructions = Checks.text(instructions); }
}
