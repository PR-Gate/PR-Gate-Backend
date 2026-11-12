package com.prgate.metrics.domain.model.valueobjects;
import java.time.Instant;
import java.util.UUID;
public record ManualDecision(CheckId checkId, boolean passed, UUID teacherId, Instant reviewedAt, String justification) {
    public ManualDecision {
        Checks.required(checkId); Checks.required(teacherId); Checks.required(reviewedAt);
        justification = Checks.text(justification);
    }
}
