package com.prgate.metrics.domain.model.valueobjects;
/** Scope must match both the reference and the observation: overall code is not new code. */
public record AnalysisScope(Kind kind, String reference) {
    public enum Kind { BRANCH, PULL_REQUEST }
    public AnalysisScope { Checks.required(kind); reference = Checks.text(reference); }
}
